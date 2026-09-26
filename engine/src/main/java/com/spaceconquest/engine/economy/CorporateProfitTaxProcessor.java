package com.spaceconquest.engine.economy;

import com.spaceconquest.engine.Corporation;
import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.Moon;
import com.spaceconquest.engine.Planet;
import com.spaceconquest.engine.Population;
import com.spaceconquest.engine.SolarSystem;
import com.spaceconquest.engine.industry.IndustrialFacility;
import com.spaceconquest.engine.industry.IndustryAccount;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Assesses realized corporate earnings, carries losses and settles local tax receipts. */
public class CorporateProfitTaxProcessor {
    public record Result(List<Corporation> corporations, List<CorporateTaxAccount> accounts,
                         Map<String, Double> collectedByBody) {}

    public Result process(GameState state) {
        Map<String, IndustrialFacility> facilities = new HashMap<>();
        for (IndustrialFacility facility : state.industrialFacilities()) facilities.put(facility.id(), facility);
        Map<String, CorporateTaxAccount> previous = new HashMap<>();
        for (CorporateTaxAccount account : state.corporateTaxAccounts()) {
            previous.put(account.corporationId(), account);
        }
        Map<String, Empire> empires = new HashMap<>();
        for (Empire empire : state.empires()) empires.put(empire.id(), empire);
        Map<String, Set<String>> eligibleBodies = eligibleBodiesByEmpire(state);
        List<Corporation> corporations = new ArrayList<>();
        List<CorporateTaxAccount> accounts = new ArrayList<>();
        Map<String, Double> collected = new HashMap<>();
        for (Corporation corporation : state.corporations()) {
            List<IndustryAccount> owned = state.industryAccounts().stream()
                    .filter(account -> {
                        IndustrialFacility facility = facilities.get(account.facilityId());
                        return facility != null && IndustrialFacility.PRIVATE_CORPORATE.equals(facility.ownershipType())
                                && corporation.id().equals(facility.ownerEntityId());
                    }).toList();
            double profit = owned.stream().mapToDouble(IndustryAccount::realizedResultCredits).sum();
            CorporateTaxAccount old = previous.get(corporation.id());
            double oldLoss = old == null ? 0.0 : old.lossCarryforwardCredits();
            double oldDue = old == null ? 0.0 : old.unpaidTaxCredits();
            double taxable = Math.max(0.0, profit - oldLoss);
            double loss = Math.max(0.0, oldLoss - profit);
            Empire empire = empires.get(corporation.empireId());
            double rate = empire == null ? 0.0 : Math.clamp(empire.corporateTaxRate(), 0.0, 1.0);
            double assessed = taxable * rate;
            Set<String> recipients = eligibleBodies.getOrDefault(corporation.empireId(), Set.of());
            double paid = recipients.isEmpty() ? 0.0
                    : Math.min(Math.max(0.0, corporation.liquidCapitalReserves()), assessed + oldDue);
            double due = Math.max(0.0, assessed + oldDue - paid);
            corporations.add(new Corporation(corporation.id(), corporation.name(), corporation.empireId(),
                    corporation.headquartersEntityId(), corporation.marketOrientation(),
                    Math.max(0.0, corporation.liquidCapitalReserves() - paid), corporation.ownedFacilityIds(),
                    corporation.ownedShipIds(), corporation.claimedVeinIds()));
            accounts.add(new CorporateTaxAccount(corporation.id(), loss, due, taxable, assessed, paid));
            distributeReceipts(corporation, owned, facilities, recipients, paid, collected);
        }
        return new Result(List.copyOf(corporations), List.copyOf(accounts), Map.copyOf(collected));
    }

    private void distributeReceipts(Corporation corporation, List<IndustryAccount> owned,
                                    Map<String, IndustrialFacility> facilities, Set<String> recipients, double paid,
                                    Map<String, Double> collected) {
        if (paid <= 0.0) return;
        Map<String, Double> weights = new HashMap<>();
        for (IndustryAccount account : owned) {
            double positive = Math.max(0.0, account.realizedResultCredits());
            String bodyId = facilities.get(account.facilityId()).planetId();
            if (positive > 0.0 && recipients.contains(bodyId)) {
                weights.merge(bodyId, positive, Double::sum);
            }
        }
        double total = weights.values().stream().mapToDouble(Double::doubleValue).sum();
        if (total <= 0.0) {
            String fallback = recipients.contains(corporation.headquartersEntityId())
                    ? corporation.headquartersEntityId() : recipients.iterator().next();
            collected.merge(fallback, paid, Double::sum);
            return;
        }
        weights.forEach((bodyId, weight) -> collected.merge(bodyId, paid * weight / total, Double::sum));
    }

    private Map<String, Set<String>> eligibleBodiesByEmpire(GameState state) {
        Map<String, Set<String>> bodies = new HashMap<>();
        Map<String, String> systemOwners = new HashMap<>();
        for (Empire empire : state.empires()) {
            for (String systemId : empire.controlledSystemIds()) {
                systemOwners.putIfAbsent(systemId, empire.id());
            }
        }
        for (Empire empire : state.empires()) {
            Set<String> populated = new LinkedHashSet<>();
            for (SolarSystem system : state.solarSystems()) {
                if (!empire.id().equals(systemOwners.get(system.id()))) continue;
                for (Planet planet : system.planets()) {
                    if (hasPopulation(planet.populations())) populated.add(planet.id());
                    for (Moon moon : planet.moons()) {
                        if (hasPopulation(moon.populations())) populated.add(moon.id());
                    }
                }
            }
            bodies.put(empire.id(), populated);
        }
        return bodies;
    }

    private boolean hasPopulation(List<Population> populations) {
        return populations != null && populations.stream().anyMatch(population -> population.totalCount() > 0);
    }
}
