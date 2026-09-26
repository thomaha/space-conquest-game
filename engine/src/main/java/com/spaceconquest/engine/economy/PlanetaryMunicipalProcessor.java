package com.spaceconquest.engine.economy;

import com.spaceconquest.engine.CommercialHub;
import com.spaceconquest.engine.CourierShip;
import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.MinistryAssignment;
import com.spaceconquest.engine.Moon;
import com.spaceconquest.engine.Planet;
import com.spaceconquest.engine.Population;
import com.spaceconquest.engine.SolarSystem;
import com.spaceconquest.engine.industry.IndustrialFacility;
import com.spaceconquest.engine.industry.IndustryAccount;
import com.spaceconquest.engine.industry.PowerGridState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Simulates authoritative turn-based municipal finances across all colonized celestial bodies.
 * Computes localized revenues, operating expenditures, liquid reserves, outstanding debt,
 * central subsidies and currency courier dispatches.
 */
public class PlanetaryMunicipalProcessor {

    public static final String TECH_SUBSPACE_BANKING = "tech_subspace_banking";

    /** Creates truthful zero-day accounts without charging the public budget before turn one. */
    public List<PlanetaryBalanceSheet> createOpeningBalanceSheets(GameState state) {
        List<PlanetaryBalanceSheet> opening = new ArrayList<>();
        for (SolarSystem system : state.solarSystems()) {
            Empire empire = findSystemEmpire(system.id(), state.empires());
            if (empire == null) continue;
            for (Planet planet : system.planets()) {
                if (countPopulation(planet.populations()) > 0) {
                    opening.add(PlanetaryBalanceSheet.createEmpty(planet.id(), system.id(), empire.id()));
                }
                for (Moon moon : planet.moons()) {
                    if (countPopulation(moon.populations()) > 0) {
                        opening.add(PlanetaryBalanceSheet.createEmpty(moon.id(), system.id(), empire.id()));
                    }
                }
            }
        }
        return opening;
    }

    /**
     * Executes the municipal finance pass for the provided game state snapshot.
     *
     * @param state simulation game state
     * @return municipal turn result containing balance sheets, dispatched couriers, and updated empires
     */
    public MunicipalTurnResult processMunicipalFinances(GameState state) {
        return processMunicipalFinances(state, Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
    }

    public MunicipalTurnResult processMunicipalFinances(GameState state,
            HouseholdEconomyProcessor.TurnResult households) {
        return processMunicipalFinances(state, households.incomeTaxByBody(),
                households.grossWagesByBody(), households.publicWagesByBody(),
                households.welfareByBody(), Map.of());
    }

    public MunicipalTurnResult processMunicipalFinances(GameState state,
            HouseholdEconomyProcessor.TurnResult households, Map<String, Double> corporateTaxByBody) {
        return processMunicipalFinances(state, households.incomeTaxByBody(),
                households.grossWagesByBody(), households.publicWagesByBody(),
                households.welfareByBody(), corporateTaxByBody);
    }

    private MunicipalTurnResult processMunicipalFinances(GameState state,
            Map<String, Double> incomeTaxByBody, Map<String, Double> grossWagesByBody,
            Map<String, Double> publicWagesByBody, Map<String, Double> welfareByBody,
            Map<String, Double> corporateTaxByBody) {
        if (state == null) {
            return new MunicipalTurnResult(List.of(), List.of(), List.of(), Map.of());
        }

        Map<String, PlanetaryBalanceSheet> previousSheetMap = buildPreviousSheetMap(state.planetaryBalanceSheets());
        Map<String, SystemEconomy> systemEconomyMap = buildSystemEconomyMap(state.systemEconomies());
        Map<String, Double> treasuryDeltas = new HashMap<>();

        List<PlanetaryBalanceSheet> balanceSheets = new ArrayList<>();
        List<CourierShip> dispatchedCouriers = new ArrayList<>();

        for (SolarSystem sys : state.solarSystems()) {
            Empire systemEmpire = findSystemEmpire(sys.id(), state.empires());
            if (systemEmpire == null) continue;

            boolean isHiveMind = isHiveMindSociety(systemEmpire);
            SystemEconomy sysEconomy = systemEconomyMap.get(sys.id());
            if (sysEconomy != null && !systemEmpire.id().equals(sysEconomy.empireId())) {
                sysEconomy = null;
            }
            double financeMinistrySynergy = calculateFinanceMinistrySynergy(systemEmpire);
            double governorSynergy = calculateGovernorSynergy(sys.id(), systemEmpire);

            long totalSystemPopulation = calculateSystemPopulation(sys);
            double systemSubsidy = isHiveMind || totalSystemPopulation <= 0 ? 0.0
                    : reserveSystemSubsidy(systemEmpire, sysEconomy, treasuryDeltas);

            for (Planet planet : sys.planets()) {
                long planetPop = countPopulation(planet.populations());
                if (planetPop > 0) {
                    processBodyFinances(
                            planet.id(), sys.id(), systemEmpire, isHiveMind, planetPop, totalSystemPopulation,
                            financeMinistrySynergy, governorSynergy,
                            sysEconomy, systemSubsidy, state, previousSheetMap, treasuryDeltas, balanceSheets,
                            dispatchedCouriers, incomeTaxByBody, grossWagesByBody, publicWagesByBody,
                            welfareByBody, corporateTaxByBody
                    );
                }

                if (planet.moons() != null) {
                    for (Moon moon : planet.moons()) {
                        long moonPop = countPopulation(moon.populations());
                        if (moonPop > 0) {
                            processBodyFinances(
                                    moon.id(), sys.id(), systemEmpire, isHiveMind, moonPop, totalSystemPopulation,
                                    financeMinistrySynergy, governorSynergy,
                                    sysEconomy, systemSubsidy, state, previousSheetMap, treasuryDeltas, balanceSheets,
                                    dispatchedCouriers, incomeTaxByBody, grossWagesByBody, publicWagesByBody,
                                    welfareByBody, corporateTaxByBody
                            );
                        }
                    }
                }
            }
        }

        Set<String> processedBodies = new HashSet<>();
        for (PlanetaryBalanceSheet sheet : balanceSheets) processedBodies.add(sheet.planetId());
        for (PlanetaryBalanceSheet previous : state.planetaryBalanceSheets()) {
            if ((previous.outstandingDebtCredits() > 0 || previous.uncollectedLocalCredits() > 0)
                    && !processedBodies.contains(previous.planetId())) {
                balanceSheets.add(previous);
            }
        }

        List<Empire> updatedEmpires = applyTreasuryDeltas(state.empires(), treasuryDeltas);
        Map<String, Double> newImperialDebt = new HashMap<>();
        for (Empire empire : state.empires()) {
            double closingPosition = empire.treasuryCredits() + treasuryDeltas.getOrDefault(empire.id(), 0.0);
            if (closingPosition < 0.0) newImperialDebt.put(empire.id(), -closingPosition);
        }
        return new MunicipalTurnResult(balanceSheets, dispatchedCouriers, updatedEmpires, newImperialDebt);
    }

    private void processBodyFinances(
            String bodyId,
            String systemId,
            Empire empire,
            boolean isHiveMind,
            long bodyPopulation,
            long systemPopulation,
            double financeMinistrySynergy,
            double governorSynergy,
            SystemEconomy sysEconomy,
            double systemSubsidy,
            GameState state,
            Map<String, PlanetaryBalanceSheet> previousSheetMap,
            Map<String, Double> treasuryDeltas,
            List<PlanetaryBalanceSheet> balanceSheets,
            List<CourierShip> dispatchedCouriers,
            Map<String, Double> incomeTaxByBody,
            Map<String, Double> grossWagesByBody,
            Map<String, Double> publicWagesByBody,
            Map<String, Double> welfareByBody,
            Map<String, Double> corporateTaxByBody
    ) {
        if (isHiveMind) {
            PlanetaryBalanceSheet previous = previousSheetMap.get(bodyId);
            double debt = previous != null ? previous.outstandingDebtCredits() : 0.0;
            balanceSheets.add(PlanetaryBalanceSheet.createEmpty(bodyId, systemId, empire.id())
                    .withOutstandingDebt(debt));
            return;
        }

        double previousUncollected = 0.0;
        double previousDebt = 0.0;
        PlanetaryBalanceSheet prev = previousSheetMap.get(bodyId);
        if (prev != null) {
            previousUncollected = prev.uncollectedLocalCredits();
            previousDebt = prev.outstandingDebtCredits();
        }

        // Revenues
        double totalCitizenWages = grossWagesByBody.getOrDefault(bodyId, 0.0);
        double incomeTax = incomeTaxByBody.getOrDefault(bodyId, 0.0);

        double corporateValue = calculateCorporateProductionValue(bodyId, state);
        double corporateTax = corporateTaxByBody.getOrDefault(bodyId, 0.0);

        double dockingFees = calculateDockingFees(bodyId, state.commercialHubs(), financeMinistrySynergy);
        double totalRevenue = incomeTax + corporateTax + dockingFees;
        double grossProduct = totalCitizenWages + corporateValue + (dockingFees * 5.0);

        // Operating Expenditures
        double stateSalaries = publicWagesByBody.getOrDefault(bodyId, 0.0);
        double facilityMaintenance = calculateFacilityMaintenance(bodyId, empire.id(), state.industrialFacilities());
        double welfareExpenses = welfareByBody.getOrDefault(bodyId, 0.0);
        double infraUpkeep = calculateInfrastructureUpkeep(bodyId, state.powerGrids());
        double bodyShare = systemPopulation > 0 ? (double) bodyPopulation / systemPopulation : 0.0;
        double publicFunding = sysEconomy != null ? sysEconomy.totalBudgetCredits() * bodyShare : 0.0;
        double totalExpenditures = stateSalaries + facilityMaintenance + welfareExpenses + infraUpkeep + publicFunding;

        double netBalance = totalRevenue - totalExpenditures;
        double centralSubsidy = systemSubsidy * bodyShare;
        double localPosition = previousUncollected - previousDebt + centralSubsidy + netBalance;
        double uncollected = Math.max(0.0, localPosition);
        double outstandingDebt = Math.max(0.0, -localPosition);
        double requestedContribution = sysEconomy != null
                ? Math.max(0.0, sysEconomy.empireContributionRate()) * sysEconomy.totalBudgetCredits() * bodyShare
                : 0.0;
        double empireTransfer = Math.min(uncollected, requestedContribution);
        uncollected -= empireTransfer;
        dispatchContribution(empireTransfer, bodyId, systemId, empire, treasuryDeltas, dispatchedCouriers);

        balanceSheets.add(new PlanetaryBalanceSheet(
                bodyId, systemId, empire.id(),
                grossProduct, incomeTax, corporateTax, dockingFees, totalRevenue,
                stateSalaries, facilityMaintenance, welfareExpenses, infraUpkeep, totalExpenditures,
                netBalance, uncollected, centralSubsidy, publicFunding,
                empireTransfer - centralSubsidy, outstandingDebt
        ));
    }

    private double reserveSystemSubsidy(Empire empire, SystemEconomy economy, Map<String, Double> treasuryDeltas) {
        if (economy == null || economy.empireContributionRate() >= 0.0) return 0.0;
        double requested = -economy.empireContributionRate() * economy.totalBudgetCredits();
        treasuryDeltas.merge(empire.id(), -requested, Double::sum);
        return requested;
    }

    private void dispatchContribution(double credits, String bodyId, String systemId, Empire empire,
                                      Map<String, Double> treasuryDeltas, List<CourierShip> couriers) {
        if (credits <= 0.0) return;
        if (empire.unlockedTechIds() != null && empire.unlockedTechIds().contains(TECH_SUBSPACE_BANKING)) {
            treasuryDeltas.merge(empire.id(), credits, Double::sum);
            return;
        }
        String destination = empire.controlledSystemIds().isEmpty() ? systemId : empire.controlledSystemIds().get(0);
        couriers.add(new CourierShip("courier_tax_" + bodyId + "_" + java.util.UUID.randomUUID(),
                empire.id(), credits, systemId, destination, 2, false));
    }

    private double calculateCorporateProductionValue(String bodyId, GameState state) {
        double val = 0.0;
        Map<String, IndustrialFacility> facilities = new HashMap<>();
        for (IndustrialFacility facility : state.industrialFacilities()) facilities.put(facility.id(), facility);
        for (IndustryAccount account : state.industryAccounts()) {
            IndustrialFacility facility = facilities.get(account.facilityId());
            if (facility != null && bodyId.equalsIgnoreCase(facility.planetId())
                    && IndustrialFacility.PRIVATE_CORPORATE.equals(facility.ownershipType())) {
                val += account.salesCredits();
            }
        }
        return val;
    }

    private double calculateDockingFees(String bodyId, List<CommercialHub> hubs, double financeSynergy) {
        if (hubs == null) return 0.0;
        for (CommercialHub h : hubs) {
            if (bodyId.equalsIgnoreCase(h.entityId())) {
                return 150.0 * h.transactionTariffRate() * 10.0 * financeSynergy;
            }
        }
        return 0.0;
    }

    private double calculateFacilityMaintenance(String bodyId, String empireId, List<IndustrialFacility> facilities) {
        if (facilities == null) return 0.0;
        double upkeep = 0.0;
        for (IndustrialFacility f : facilities) {
            if (bodyId.equalsIgnoreCase(f.planetId()) && ("PUBLIC_STATE".equalsIgnoreCase(f.ownershipType()) || empireId.equalsIgnoreCase(f.ownerEntityId()))) {
                upkeep += 80.0 * f.tier();
            }
        }
        return upkeep;
    }

    private double calculateInfrastructureUpkeep(String bodyId, List<PowerGridState> grids) {
        if (grids == null) return 50.0;
        for (PowerGridState grid : grids) {
            if (bodyId.equalsIgnoreCase(grid.entityId())) {
                return 50.0 + (grid.batteryCapacityKwh() * 0.01);
            }
        }
        return 50.0;
    }

    private double calculateFinanceMinistrySynergy(Empire empire) {
        if (empire.ministries() == null) return 1.0;
        for (MinistryAssignment ma : empire.ministries()) {
            if ("ministry_finance_commerce".equalsIgnoreCase(ma.portfolioId())) {
                return Math.max(1.0, ma.calculatedEfficiencyModifier());
            }
        }
        return 1.0;
    }

    private double calculateGovernorSynergy(String systemId, Empire empire) {
        if (empire.systemGovernorAssignments() == null) return 0.90;
        return empire.systemGovernorAssignments().containsKey(systemId) ? 1.0 : 0.90;
    }

    private boolean isHiveMindSociety(Empire empire) {
        if (empire.societyStructure() == null) return false;
        return empire.societyStructure().toLowerCase().contains("hive");
    }

    private long calculateSystemPopulation(SolarSystem sys) {
        long pop = 0L;
        if (sys.planets() != null) {
            for (Planet p : sys.planets()) {
                pop += countPopulation(p.populations());
                if (p.moons() != null) {
                    for (Moon m : p.moons()) {
                        pop += countPopulation(m.populations());
                    }
                }
            }
        }
        return pop;
    }

    private long countPopulation(List<Population> populations) {
        if (populations == null) return 0L;
        long total = 0L;
        for (Population p : populations) {
            total += p.totalCount();
        }
        return total;
    }

    private Empire findSystemEmpire(String systemId, List<Empire> empires) {
        if (empires == null) return null;
        for (Empire emp : empires) {
            if (emp.controlledSystemIds() != null && emp.controlledSystemIds().contains(systemId)) {
                return emp;
            }
        }
        return null;
    }

    private Map<String, PlanetaryBalanceSheet> buildPreviousSheetMap(List<PlanetaryBalanceSheet> sheets) {
        Map<String, PlanetaryBalanceSheet> map = new HashMap<>();
        if (sheets != null) {
            for (PlanetaryBalanceSheet s : sheets) {
                map.put(s.planetId(), s);
            }
        }
        return map;
    }

    private Map<String, SystemEconomy> buildSystemEconomyMap(List<SystemEconomy> economies) {
        Map<String, SystemEconomy> map = new HashMap<>();
        if (economies != null) {
            for (SystemEconomy se : economies) {
                map.put(se.systemId(), se);
            }
        }
        return map;
    }

    private List<Empire> applyTreasuryDeltas(List<Empire> originalEmpires, Map<String, Double> treasuryDeltas) {
        if (treasuryDeltas.isEmpty()) {
            return originalEmpires;
        }
        List<Empire> updated = new ArrayList<>();
        for (Empire emp : originalEmpires) {
            double delta = treasuryDeltas.getOrDefault(emp.id(), 0.0);
            if (delta == 0.0) {
                updated.add(emp);
            } else {
                double newTreasury = Math.max(0.0, emp.treasuryCredits() + delta);
                updated.add(new Empire(
                        emp.id(), emp.name(), emp.raceId(), emp.societyStructure(),
                        newTreasury, emp.corporateTaxRate(), emp.controlledSystemIds(),
                        emp.ministries(), emp.systemGovernorAssignments(),
                        emp.unlockedTechIds(), emp.activeShipDesignIds()
                ));
            }
        }
        return updated;
    }

    public record MunicipalTurnResult(
            List<PlanetaryBalanceSheet> balanceSheets,
            List<CourierShip> dispatchedCouriers,
            List<Empire> updatedEmpires,
            Map<String, Double> newImperialDebtCredits
    ) {}
}
