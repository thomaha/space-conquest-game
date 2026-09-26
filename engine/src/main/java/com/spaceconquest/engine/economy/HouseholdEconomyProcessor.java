package com.spaceconquest.engine.economy;

import com.spaceconquest.engine.CommercialHub;
import com.spaceconquest.engine.Corporation;
import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.MarketOrder;
import com.spaceconquest.engine.Moon;
import com.spaceconquest.engine.Planet;
import com.spaceconquest.engine.Population;
import com.spaceconquest.engine.PopulationProcessor;
import com.spaceconquest.engine.Profession;
import com.spaceconquest.engine.Race;
import com.spaceconquest.engine.SolarSystem;
import com.spaceconquest.engine.demographics.CitizenCohort;
import com.spaceconquest.engine.demographics.ColonyFocus;
import com.spaceconquest.engine.industry.IndustrialFacility;
import com.spaceconquest.engine.industry.PowerBillingProcessor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Pays funded jobs, collects personal tax and clears daily household demand against local stock. */
public class HouseholdEconomyProcessor {
    private static final int WORKING_AGE = 18;
    private static final int RETIREMENT_AGE = 65;
    private static final double PENSION_CREDITS_PER_RETIREE = 0.05;
    private static final String SECONDARY_GOOD = "consumer_goods";
    private static final String LUXURY_GOOD = "luxury_goods";
    private final PopulationProcessor populationProcessor = new PopulationProcessor();

    public TurnResult process(GameState state, List<Race> races) {
        Map<String, Race> raceById = new HashMap<>();
        for (Race race : races) raceById.put(race.id(), race);
        Map<String, HouseholdAccount> previous = new HashMap<>();
        for (HouseholdAccount account : state.householdAccounts()) previous.put(account.key(), account);
        Map<String, CommercialHub> hubs = new LinkedHashMap<>();
        for (CommercialHub hub : state.commercialHubs()) hubs.put(hub.id(), hub);
        Map<String, MarketAccount> marketAccounts = new HashMap<>();
        for (MarketAccount account : state.marketAccounts()) marketAccounts.put(account.hubId(), account);
        Map<String, Corporation> corporations = new LinkedHashMap<>();
        for (Corporation corporation : state.corporations()) corporations.put(corporation.id(), corporation);
        Map<String, Empire> empires = new LinkedHashMap<>();
        for (Empire empire : state.empires()) empires.put(empire.id(), empire);
        Map<String, Integer> industryJobs = new HashMap<>();
        for (IndustrialFacility facility : state.industrialFacilities()) {
            industryJobs.put(facility.id(), Math.max(0, facility.allocatedWorkers()));
        }
        Map<String, Integer> paidWorkersByFacility = new HashMap<>();
        Map<String, Double> wagesByFacility = new HashMap<>();
        Map<String, Double> taxByBody = new HashMap<>();
        Map<String, Double> grossWagesByBody = new HashMap<>();
        Map<String, Double> publicWagesByBody = new HashMap<>();
        Map<String, Double> welfareByBody = new HashMap<>();
        List<HouseholdAccount> accounts = new ArrayList<>();

        for (SolarSystem system : state.solarSystems()) {
            Empire empire = state.empires().stream()
                    .filter(candidate -> candidate.controlledSystemIds().contains(system.id()))
                    .findFirst().orElse(null);
            if (empire == null || (empire.societyStructure() != null
                    && empire.societyStructure().toLowerCase().contains("hive"))) continue;
            SystemEconomy economy = state.systemEconomies().stream()
                    .filter(item -> item.systemId().equals(system.id()) && item.empireId().equals(empire.id()))
                    .findFirst().orElse(null);
            Map<String, Long> publicJobs = publicJobs(economy);
            for (Planet planet : system.planets()) {
                processBody(planet.id(), planet.atmosphere(), system.id(), empire, planet.populations(), economy,
                        state.industrialFacilities(), raceById, previous, publicJobs, industryJobs, corporations,
                        empires, paidWorkersByFacility, wagesByFacility,
                        hubs, marketAccounts, taxByBody, grossWagesByBody, publicWagesByBody,
                        welfareByBody, accounts);
                for (Moon moon : planet.moons()) {
                    processBody(moon.id(), moon.atmosphere(), system.id(), empire, moon.populations(), economy,
                            state.industrialFacilities(), raceById, previous, publicJobs, industryJobs, corporations,
                            empires, paidWorkersByFacility, wagesByFacility,
                            hubs, marketAccounts, taxByBody, grossWagesByBody, publicWagesByBody,
                            welfareByBody, accounts);
                }
            }
        }
        Set<String> activeKeys = new HashSet<>();
        for (HouseholdAccount account : accounts) activeKeys.add(account.key());
        for (HouseholdAccount old : previous.values()) {
            if (!activeKeys.contains(old.key()) && old.savingsCredits() > 0.0) {
                accounts.add(new HouseholdAccount(old.bodyId(), old.systemId(), old.empireId(), old.raceId(),
                        old.professionId(), 0, old.savingsCredits(), 0.0, 0.0, 0.0, 0.0,
                        Map.of(), 1.0, 1.0, 0.0, 0.0));
            }
        }
        return new TurnResult(List.copyOf(accounts), List.copyOf(hubs.values()),
                List.copyOf(corporations.values()), List.copyOf(empires.values()),
                marketAccounts.values().stream()
                        .sorted(Comparator.comparing(MarketAccount::hubId)).toList(),
                Map.copyOf(taxByBody), Map.copyOf(grossWagesByBody), Map.copyOf(publicWagesByBody),
                Map.copyOf(welfareByBody), Map.copyOf(paidWorkersByFacility),
                Map.copyOf(wagesByFacility));
    }

    private void processBody(String bodyId, String atmosphere, String systemId, Empire empire, List<Population> populations,
                             SystemEconomy economy, List<IndustrialFacility> facilities, Map<String, Race> races,
                             Map<String, HouseholdAccount> previous, Map<String, Long> publicJobs,
                             Map<String, Integer> industryJobs,
                             Map<String, Corporation> corporations, Map<String, Empire> empires,
                             Map<String, Integer> paidWorkersByFacility,
                             Map<String, Double> wagesByFacility, Map<String, CommercialHub> hubs,
                             Map<String, MarketAccount> marketAccounts, Map<String, Double> taxes,
                             Map<String, Double> grossWages, Map<String, Double> publicWages,
                             Map<String, Double> welfareByBody,
                             List<HouseholdAccount> accounts) {
        Map<String, List<CitizenCohort>> groups = new TreeMap<>();
        for (Population population : populations) {
            for (CitizenCohort cohort : population.toDemographics(bodyId, systemId, ColonyFocus.BALANCED).cohorts()) {
                groups.computeIfAbsent(cohort.raceId() + "/" + cohort.professionId(), ignored -> new ArrayList<>())
                        .add(cohort);
            }
        }
        for (List<CitizenCohort> cohorts : groups.values()) {
            CitizenCohort first = cohorts.getFirst();
            long headcount = cohorts.stream().mapToLong(CitizenCohort::headcount).sum();
            long working = cohorts.stream().filter(c -> c.ageBracket() >= WORKING_AGE
                    && c.ageBracket() < RETIREMENT_AGE).mapToLong(CitizenCohort::headcount).sum();
            long retired = cohorts.stream().filter(c -> c.ageBracket() >= RETIREMENT_AGE)
                    .mapToLong(CitizenCohort::headcount).sum();
            String key = bodyId + "/" + first.raceId() + "/" + first.professionId();
            double wage = Profession.getBaseWageForProfession(first.professionId());
            long publicHired = Math.min(working, publicJobs.getOrDefault(first.professionId(), 0L));
            publicJobs.merge(first.professionId(), -publicHired, Long::sum);
            double publicPay = publicHired * wage;
            double industrialPay = payIndustryWorkers(bodyId, empire.id(), first.professionId(),
                    working - publicHired, wage, facilities, industryJobs, corporations, empires,
                    paidWorkersByFacility, wagesByFacility);
            double income = publicPay + industrialPay;
            double welfare = retired * PENSION_CREDITS_PER_RETIREE;
            double taxRate = economy == null ? 0.10 : Math.clamp(economy.taxRate(), 0.0, 0.50);
            double tax = income * taxRate;
            Wallet wallet = new Wallet(Math.max(0.0, previous.containsKey(key)
                    ? previous.get(key).savingsCredits() : 0.0) + income + welfare - tax);
            Map<String, Double> unmetBasic = new LinkedHashMap<>();
            Map<String, Double> requirements = races.containsKey(first.raceId())
                    ? populationProcessor.calculateDailyMarketRequirements(headcount, races.get(first.raceId()), atmosphere)
                    : Map.of("food_matrix", headcount * 0.1);
            requirements.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
                double bought = buy(bodyId, entry.getKey(), entry.getValue(), wallet, 0.0,
                        hubs, marketAccounts);
                if (bought + 0.000001 < entry.getValue()) unmetBasic.put(entry.getKey(), entry.getValue() - bought);
            });
            double secondaryNeed = headcount * 0.005;
            double luxuryNeed = headcount * 0.001;
            double electricityNeed = PowerBillingProcessor.householdDemandKwh(empire, headcount);
            double reserved = electricityNeed * PowerBillingProcessor.PRICE_PER_KWH;
            double secondaryMet = buy(bodyId, SECONDARY_GOOD, secondaryNeed, wallet, reserved,
                    hubs, marketAccounts);
            double luxuryMet = buy(bodyId, LUXURY_GOOD, luxuryNeed, wallet, reserved,
                    hubs, marketAccounts);
            accounts.add(new HouseholdAccount(bodyId, systemId, empire.id(), first.raceId(),
                    first.professionId(), headcount, wallet.credits, income, welfare, tax, wallet.spent,
                    unmetBasic, fraction(secondaryMet, secondaryNeed), fraction(luxuryMet, luxuryNeed),
                    0.0, electricityNeed));
            taxes.merge(bodyId, tax, Double::sum);
            grossWages.merge(bodyId, income, Double::sum);
            publicWages.merge(bodyId, publicPay, Double::sum);
            welfareByBody.merge(bodyId, welfare, Double::sum);
        }
    }

    private double payIndustryWorkers(String bodyId, String empireId, String professionId, long available,
                                      double wage, List<IndustrialFacility> facilities,
                                      Map<String, Integer> industryJobs, Map<String, Corporation> corporations,
                                      Map<String, Empire> empires, Map<String, Integer> paidWorkers,
                                      Map<String, Double> wagesByFacility) {
        double paid = 0.0;
        for (IndustrialFacility facility : facilities) {
            if (available <= 0) break;
            if (!bodyId.equals(facility.planetId()) || !professionId.equals(facility.workerProfessionId())) continue;
            Corporation corporation = corporations.get(facility.ownerEntityId());
            Empire stateOwner = empires.get(facility.ownerEntityId());
            boolean privateOwner = IndustrialFacility.PRIVATE_CORPORATE.equals(facility.ownershipType())
                    && corporation != null && empireId.equals(corporation.empireId());
            boolean publicOwner = IndustrialFacility.PUBLIC_STATE.equals(facility.ownershipType())
                    && stateOwner != null && empireId.equals(stateOwner.id());
            if (!privateOwner && !publicOwner) continue;
            double funds = privateOwner ? corporation.liquidCapitalReserves() : stateOwner.treasuryCredits();
            long hired = Math.min(available, Math.min(industryJobs.getOrDefault(facility.id(), 0),
                    (long) Math.floor(Math.max(0.0, funds) / wage)));
            if (hired <= 0) continue;
            double payment = hired * wage;
            if (privateOwner) {
                corporations.put(corporation.id(), new Corporation(corporation.id(), corporation.name(),
                        corporation.empireId(), corporation.headquartersEntityId(), corporation.marketOrientation(),
                        funds - payment, corporation.ownedFacilityIds(), corporation.ownedShipIds(),
                        corporation.claimedVeinIds()));
            } else {
                empires.put(stateOwner.id(), new Empire(stateOwner.id(), stateOwner.name(), stateOwner.raceId(),
                        stateOwner.societyStructure(), funds - payment, stateOwner.corporateTaxRate(),
                        stateOwner.controlledSystemIds(), stateOwner.ministries(),
                        stateOwner.systemGovernorAssignments(), stateOwner.unlockedTechIds(),
                        stateOwner.activeShipDesignIds()));
            }
            industryJobs.merge(facility.id(), -(int) hired, Integer::sum);
            paidWorkers.merge(facility.id(), (int) hired, Integer::sum);
            wagesByFacility.merge(facility.id(), payment, Double::sum);
            available -= hired;
            paid += payment;
        }
        return paid;
    }

    private double buy(String bodyId, String resourceId, double requested, Wallet wallet,
                       double protectedCredits,
                       Map<String, CommercialHub> hubs, Map<String, MarketAccount> accounts) {
        if (requested <= 0.0) return 0.0;
        double acquired = 0.0;
        for (CommercialHub hub : List.copyOf(hubs.values())) {
            if (!bodyId.equals(hub.entityId())) continue;
            MarketOrder order = hub.activeOrders().get(resourceId);
            if (order == null || !Double.isFinite(order.supplyKg()) || order.supplyKg() <= 0.0
                    || !Double.isFinite(order.pricePerKg()) || order.pricePerKg() < 0.0) continue;
            double price = order.pricePerKg();
            double affordable = price > 0.0
                    ? Math.max(0.0, wallet.credits - protectedCredits) / price : requested;
            double quantity = Math.min(requested - acquired, Math.min(order.supplyKg(), affordable));
            if (quantity <= 0.0) continue;
            double cost = quantity * price;
            wallet.credits = Math.max(0.0, wallet.credits - cost);
            wallet.spent += cost;
            MarketAccount previous = accounts.get(hub.id());
            accounts.put(hub.id(), new MarketAccount(hub.id(),
                    (previous == null ? 0.0 : previous.unsettledSalesCredits()) + cost));
            Map<String, MarketOrder> orders = new HashMap<>(hub.activeOrders());
            orders.put(resourceId, new MarketOrder(resourceId, order.supplyKg() - quantity,
                    order.demandKg(), order.pricePerKg(), order.shortcomingScore()));
            hubs.put(hub.id(), new CommercialHub(hub.id(), hub.entityId(), hub.transactionTariffRate(),
                    hub.storageCapacityKg(), Math.max(0.0, hub.currentStoredWeightKg() - quantity),
                    hub.logisticsRangeUnits(), orders));
            acquired += quantity;
            if (acquired >= requested) break;
        }
        return acquired;
    }

    private Map<String, Long> publicJobs(SystemEconomy economy) {
        if (economy == null) return new HashMap<>();
        Map<String, Long> jobs = new HashMap<>();
        jobs.put("teacher", economy.employedTeachers());
        jobs.put("scientist", economy.employedScientists());
        jobs.put("police", economy.employedPolice());
        jobs.put("medic", economy.employedMedics());
        jobs.put("engineer", economy.employedEngineers());
        jobs.put("technician", economy.employedTechnicians());
        jobs.put("soldier", economy.employedSoldiers());
        double requestedWages = jobs.entrySet().stream()
                .mapToDouble(entry -> entry.getValue() * Profession.getBaseWageForProfession(entry.getKey()))
                .sum();
        double fundedShare = requestedWages <= 0.0 ? 0.0
                : Math.min(1.0, Math.max(0.0, economy.totalBudgetCredits()) / requestedWages);
        jobs.replaceAll((profession, count) -> (long) Math.floor(count * fundedShare));
        return jobs;
    }

    private double fraction(double received, double requested) {
        return requested <= 0.0 ? 1.0 : Math.clamp(received / requested, 0.0, 1.0);
    }

    private static final class Wallet {
        private double credits;
        private double spent;

        private Wallet(double credits) {
            this.credits = credits;
        }
    }

    public record TurnResult(List<HouseholdAccount> householdAccounts, List<CommercialHub> commercialHubs,
                             List<Corporation> corporations, List<Empire> empires,
                             List<MarketAccount> marketAccounts,
                             Map<String, Double> incomeTaxByBody, Map<String, Double> grossWagesByBody,
                             Map<String, Double> publicWagesByBody, Map<String, Double> welfareByBody,
                             Map<String, Integer> paidWorkersByFacility,
                             Map<String, Double> wagesByFacility) {}
}
