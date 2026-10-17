package com.spaceconquest.frontend;

import com.spaceconquest.control.command.ResolveApplicationResearchCommand;
import com.spaceconquest.engine.technology.ApplicationRefinement;
import com.spaceconquest.engine.technology.ResearchVarianceResult;

import com.spaceconquest.control.HumanController;
import com.spaceconquest.control.command.CancelResearchCommand;
import com.spaceconquest.control.command.ShareTechnologyCommand;
import com.spaceconquest.control.command.RevokeTechnologyExchangeCommand;
import com.spaceconquest.control.command.SelectOptimizationPathCommand;
import com.spaceconquest.control.command.StartResearchCommand;
import com.spaceconquest.engine.DataModelLoader;
import com.spaceconquest.engine.GameState;
import com.spaceconquest.engine.TechnicalApplication;
import com.spaceconquest.engine.Technology;
import com.spaceconquest.engine.Empire;
import com.spaceconquest.engine.economy.SystemEconomy;
import com.spaceconquest.engine.technology.ResearchProject;
import com.spaceconquest.engine.technology.ApplicationProduction;
import com.spaceconquest.engine.technology.TechnologyExchangeRoute;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Spinner;
import javafx.scene.control.ComboBox;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * A UI component that visualizes the technology tree, active research endeavors,
 * dual-path optimization options and reverse-engineering salvage queues with direct player command dispatching.
 */
public class TechnologyView {
    private static final Logger logger = LogManager.getLogger(TechnologyView.class);

    private VBox root;
    private VBox content;
    private ScrollPane scrollPane;
    private Label feedbackLabel;
    private final Menubar menubar;
    private HumanController humanController;
    private String playerEmpireId = "terran_confederation";
    private final List<ResearchProject> activeResearchProjects = new ArrayList<>();
    private final List<TechnologyExchangeRoute> exchangeRoutes = new ArrayList<>();
    private final List<SystemEconomy> systemEconomies = new ArrayList<>();
    private Empire playerEmpire;
    private GameState snapshot = new GameState();
    private List<Technology> technologies = List.of();
    private List<com.spaceconquest.engine.Material> materials = List.of();
    private com.spaceconquest.engine.technology.ResearchFunding funding;
    private final ResearchHistoryCard researchHistoryCard = new ResearchHistoryCard(Boolean.getBoolean("spaceconquest.debug.research"));
    private long totalScientists = 0;
    private long unassignedScientists = 0;

    public TechnologyView(Menubar menubar) {
        this.menubar = menubar;
        build();
    }

    public void setHumanController(HumanController controller) {
        this.humanController = controller;
    }

    public void setPlayerEmpireId(String empireId) {
        if (empireId != null && !empireId.isEmpty()) {
            this.playerEmpireId = empireId;
        }
    }

    private void build() {
        root = new VBox(15);
        content = new VBox(15);
        scrollPane = new ScrollPane(content);

        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: rgba(12, 20, 42, 0.95); " +
                "-fx-border-color: #78aaff; -fx-border-width: 2; " +
                "-fx-border-radius: 10; -fx-background-radius: 10;");
        root.setPrefSize(880, 680);

        Text title = new Text("Imperial technology and research laboratory");
        title.setFill(Color.WHITE);
        title.setFont(Font.font("Verdana", FontWeight.BOLD, 22));

        Button closeButton = new Button("Close");
        closeButton.setStyle("-fx-background-color: #c0392b; -fx-text-fill: white; -fx-font-weight: bold;");
        closeButton.setOnAction(e -> {
            root.setVisible(false);
            if (menubar != null) {
                menubar.closePage();
            }
        });

        HBox header = new HBox(title);
        header.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(title, Priority.ALWAYS);

        javafx.scene.layout.Region spacer = new javafx.scene.layout.Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        header.getChildren().addAll(spacer, closeButton);

        feedbackLabel = new Label("Ready | Assign imperial scientists to commence research.");
        feedbackLabel.setTextFill(Color.LIGHTCYAN);
        feedbackLabel.setFont(Font.font("Verdana", 11));

        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        scrollPane.setPadding(new Insets(10));

        root.getChildren().addAll(header, feedbackLabel, scrollPane);
        root.setVisible(false);
    }

    public VBox getRoot() {
        return root;
    }

    public void show() {
        loadData();
        root.setVisible(true);
        root.toFront();
    }

    public void hide() {
        root.setVisible(false);
        if (menubar != null) {
            menubar.closePage();
        }
    }

    public void setResearchProjects(List<ResearchProject> projects) {
        activeResearchProjects.clear();
        if (projects != null) {
            activeResearchProjects.addAll(projects);
        }
        if (root.isVisible()) {
            loadData();
        }
    }

    public void updateData(GameState state) {
        if (state == null) return;
        snapshot = state;
        activeResearchProjects.clear();
        activeResearchProjects.addAll(state.researchProjects());
        exchangeRoutes.clear();
        exchangeRoutes.addAll(state.technologyExchangeRoutes());
        systemEconomies.clear();
        systemEconomies.addAll(state.systemEconomies());
        playerEmpire = state.empires().stream().filter(empire -> empire.id().equals(playerEmpireId))
                .findFirst().orElse(null);

        if (root != null && root.isVisible()) {
            loadData();
        }
    }

    private void loadData() {
        content.getChildren().clear();
        try { technologies = DataModelLoader.loadTechnologies(); }
        catch (IOException failure) { logger.error("Failed to load technology catalog", failure); technologies = List.of(); }
        try {
            materials = DataModelLoader.loadMaterials();
            funding = playerEmpire == null ? null : com.spaceconquest.engine.technology.ResearchFunding.quote(
                    snapshot, playerEmpire, DataModelLoader.loadRaces(), technologies, new com.spaceconquest.engine.technology.ResearchProcessor());
        } catch (IOException failure) { logger.error("Failed to load research preview data", failure); funding = null; }

        // 0. Summary Section (Scientists headcount)
        content.getChildren().add(createScientistSummarySection());

        // 1. Active Research Section
        content.getChildren().add(createActiveResearchSection());
        content.getChildren().add(researchHistoryCard.create(snapshot, playerEmpireId, technologies));

        // 2. Technology Exchange Accords Section
        content.getChildren().add(createExchangeAccordsSection());

        // 3. Reverse Engineering Salvage Section
        content.getChildren().add(createReverseEngineeringSection());

        // 4. Foundational Tech Tree Section
        try {
            technologies = DataModelLoader.loadTechnologies();
            Text techTreeHeader = new Text("Available technologies and practical applications");
            techTreeHeader.setFill(Color.LIGHTBLUE);
            techTreeHeader.setFont(Font.font("Verdana", FontWeight.BOLD, 16));
            content.getChildren().add(techTreeHeader);

            for (Technology tech : technologies) {
                content.getChildren().add(createTechBox(tech));
            }
        } catch (IOException e) {
            logger.error("Failed to load technologies for visualization", e);
            Text errorText = new Text("Error loading technology data.");
            errorText.setFill(Color.RED);
            content.getChildren().add(errorText);
        }
    }

    private VBox createActiveResearchSection() {
        VBox section = new VBox(10);
        section.setPadding(new Insets(12));
        section.setStyle("-fx-background-color: rgba(30, 45, 75, 0.7); -fx-background-radius: 8; -fx-border-color: #4a90e2; -fx-border-width: 1; -fx-border-radius: 8;");

        Text sectionTitle = new Text("Active research projects and progress vectors");
        sectionTitle.setFill(Color.GOLD);
        sectionTitle.setFont(Font.font("Verdana", FontWeight.BOLD, 15));
        section.getChildren().add(sectionTitle);

        var playerProjects = activeResearchProjects.stream().filter(project -> project.empireId().equals(playerEmpireId)).toList();
        if (playerProjects.isEmpty()) {
            Text emptyText = new Text("No active research project currently in progress. Select a technology below to assign scientists.");
            emptyText.setFill(Color.LIGHTGRAY);
            emptyText.setFont(Font.font("Verdana", 12));
            section.getChildren().add(emptyText);
        } else {
            for (ResearchProject project : playerProjects) {

                VBox projBox = new VBox(6);
                projBox.setPadding(new Insets(8));
                projBox.setStyle("-fx-background-color: rgba(20, 30, 50, 0.6); -fx-background-radius: 5;");

                HBox header = new HBox(10);
                header.setAlignment(Pos.CENTER_LEFT);
                Text name = new Text("Target: " + com.spaceconquest.engine.technology.ResearchFeedback.name(project.targetTechOrAppId(), technologies)
                        + (project.isApplication() ? " (Application)" : " (Foundational)"));
                name.setFill(Color.LIGHTCYAN);
                name.setFont(Font.font("Verdana", FontWeight.BOLD, 13));

                Text scientists = new Text("Requested: " + project.assignedScientists() + " scientists"
                        + (project.assignedScientists() == 0 ? " (paused)" : ""));
                scientists.setFill(Color.LIGHTGREEN);
                scientists.setFont(Font.font("Verdana", 11));

                Text speedMod = new Text(String.format("Multiplier: %.2fx", project.speedModifier()));
                speedMod.setFill(Color.YELLOW);
                speedMod.setFont(Font.font("Verdana", 11));

                header.getChildren().addAll(name, scientists, speedMod);

                ProgressBar bar = new ProgressBar(project.accumulatedPoints() / Math.max(1.0, project.requiredPoints()));
                bar.setPrefWidth(400);

                Text progressText = new Text(String.format("Progress: %.1f / %.1f pts (%.1f%%)",
                        project.accumulatedPoints(), project.requiredPoints(), project.getProgressPercentage()));
                progressText.setFill(Color.WHITE);
                progressText.setFont(Font.font("Verdana", 11));

                HBox barRow = new HBox(10, bar, progressText);
                barRow.setAlignment(Pos.CENTER_LEFT);

                Text breakthroughText = new Text("Breakthrough variance: Weighted variance roll active for this vector.");
                breakthroughText.setFill(Color.LIGHTSKYBLUE);
                breakthroughText.setFont(Font.font("Verdana", 9));

                projBox.getChildren().addAll(header, barRow, breakthroughText);
                projBox.getChildren().add(createProjectEstimate(project));
                projBox.getChildren().addAll(ResearchHistoryCard.reason(project), ResearchHistoryCard.context(snapshot, project, technologies));
                projBox.getChildren().add(createResearchAssignmentControls(project));
                section.getChildren().add(projBox);
            }
        }

        return section;
    }

    private Text createProjectEstimate(ResearchProject project) {
        String message = "Estimate unavailable.";
        if (funding != null) {
            var estimate = com.spaceconquest.engine.technology.ResearchFeedback.estimate(playerEmpire, project, technologies, funding);
            message = String.format("Funded: %.1f scientists | %.2f points/day | Exchange bonus: %.0f%%",
                    estimate.fundedScientists(), estimate.pointsPerDay(), estimate.exchangeBonus() * 100);
            message += estimate.daysRemaining() == null ? " | No completion estimate" : " | Estimated " + estimate.daysRemaining() + " days remaining";
            if (!estimate.reason().isEmpty()) message += "\n" + estimate.reason();
            message += "\nEstimate assumes current staffing, funding and exchange eligibility continue; payroll settles again next tick.";
        }
        Text text = new Text(message); text.setId("research-estimate-" + project.id());
        text.setFill(Color.LIGHTSKYBLUE); text.setFont(Font.font("Verdana", 11)); text.setWrappingWidth(740);
        return text;
    }

    private String researchAvailability(String target, boolean application) {
        return com.spaceconquest.engine.technology.ResearchFeedback.availability(snapshot, playerEmpire, target, application, technologies);
    }

    private Text researchStatus(String target, boolean application) {
        String reason = researchAvailability(target, application);
        Text status = new Text(reason.isEmpty() ? "Ready to assign funded scientists." : reason);
        status.setId("research-status-" + target); status.setFill(reason.isEmpty() ? Color.LIGHTGREEN : Color.ORANGE);
        status.setWrappingWidth(700); status.setFont(Font.font("Verdana", 10)); return status;
    }

    private VBox createScientistSummarySection() {
        VBox section = new VBox(5);
        section.setPadding(new Insets(10));
        section.setStyle("-fx-background-color: rgba(40, 60, 90, 0.6); -fx-background-radius: 8;");

        long totalScientists = playerEmpire == null ? 0 : com.spaceconquest.engine.technology.ResearchWorkforce.capacity(snapshot, playerEmpire);
        long assignedScientists = activeResearchProjects.stream()
                .filter(p -> p.empireId().equals(playerEmpireId))
                .mapToLong(ResearchProject::assignedScientists)
                .sum();
        long unassigned = Math.max(0, totalScientists - assignedScientists);

        this.totalScientists = totalScientists;
        this.unassignedScientists = unassigned;

        Text summaryTitle = new Text("Imperial scientific personnel allocation");
        summaryTitle.setFill(Color.LIGHTBLUE);
        summaryTitle.setFont(Font.font("Verdana", FontWeight.BOLD, 14));

        Text headcount = new Text(String.format("Funded scientists: %d | Requested assignments: %d | Unassigned: %d",
                totalScientists, assignedScientists, unassigned));
        headcount.setId("research-scientists");
        headcount.setFill(Color.WHITE);
        headcount.setFont(Font.font("Verdana", 12));

        section.getChildren().addAll(summaryTitle, headcount);
        return section;
    }
    private HBox createResearchAssignmentControls(ResearchProject project) {
        int limit = (int) Math.min(Integer.MAX_VALUE, Math.max(project.assignedScientists(),
                unassignedScientists + project.assignedScientists()));
        var count = new Spinner<Integer>(0, limit, Math.min(limit, Math.max(0, project.assignedScientists())));
        count.setEditable(true); count.setPrefWidth(90);
        var assign = new Button("Update assignment"); var pause = new Button("Pause");
        var cancel = new Button("Cancel and discard progress");
        count.setId("research-assignment-" + project.id()); assign.setId("research-update-" + project.id());
        pause.setId("research-pause-" + project.id()); cancel.setId("research-cancel-" + project.id());
        assign.setDisable(humanController == null); pause.setDisable(humanController == null); cancel.setDisable(humanController == null);
        assign.setOnAction(event -> {
            humanController.stageCommand(new StartResearchCommand(playerEmpireId, project.targetTechOrAppId(), project.isApplication(), count.getValue()));
            feedbackLabel.setText("Research assignment queued. Funding is checked when the command executes.");
        });
        pause.setOnAction(event -> {
            humanController.stageCommand(new StartResearchCommand(playerEmpireId, project.targetTechOrAppId(), project.isApplication(), 0));
            feedbackLabel.setText("Research pause queued; accumulated progress is retained.");
        });
        cancel.setOnAction(event -> {
            humanController.stageCommand(new CancelResearchCommand(playerEmpireId, project.id()));
            feedbackLabel.setText("Research cancellation queued; this project's progress will be discarded.");
        });
        return new HBox(8, count, assign, pause, cancel);
    }

    private VBox createExchangeAccordsSection() {
        VBox section = new VBox(8);
        section.setPadding(new Insets(10));
        section.setStyle("-fx-background-color: rgba(35, 55, 85, 0.65); -fx-background-radius: 8; -fx-border-color: #27ae60; -fx-border-width: 1; -fx-border-radius: 8;");

        Text title = new Text("Technology exchange and bilateral accords");
        title.setFill(Color.LIGHTGREEN);
        title.setFont(Font.font("Verdana", FontWeight.BOLD, 14));
        section.getChildren().add(title);

        List<TechnologyExchangeRoute> myRoutes = exchangeRoutes.stream()
                .filter(r -> r.receiverEmpireId().equals(playerEmpireId) || r.senderEmpireId().equals(playerEmpireId))
                .toList();

        if (myRoutes.isEmpty()) {
            Text empty = new Text("No active technology exchange accords established with other empires.");
            empty.setFill(Color.LIGHTGRAY);
            empty.setFont(Font.font("Verdana", 11));
            section.getChildren().add(empty);
        } else {
            for (TechnologyExchangeRoute route : myRoutes) {
                String desc = route.senderEmpireId().equals(playerEmpireId) ?
                        String.format("Sharing %s with %s (Bonus: %.0f%% training speed)",
                                route.technologyId(), route.receiverEmpireId(), route.trainingSpeedBonus() * 100) :
                                String.format("Receiving %s insights from %s (Starting progress offer: %.0f%%)",
                                route.technologyId(), route.senderEmpireId(), route.prePopulatedPercentage() * 100);

                Text routeText = new Text("• " + desc + (com.spaceconquest.engine.technology.ResearchExchange.eligible(snapshot, route, technologies)
                        ? " | Active for eligible funded research" : " | Suspended: alliance, source knowledge or prerequisites unavailable"));
                routeText.setFill(Color.GAINSBORO);
                routeText.setFont(Font.font("Verdana", 11));
                section.getChildren().add(routeText);
                var revoke = new Button("End exchange"); revoke.setDisable(humanController == null);
                revoke.setOnAction(event -> humanController.stageCommand(new RevokeTechnologyExchangeCommand(playerEmpireId, route.routeId())));
                section.getChildren().add(revoke);
            }
        }

        section.getChildren().add(createExchangeControls());
        return section;
    }
    private HBox createExchangeControls() {
        var receiver = new ComboBox<String>(); receiver.setPromptText("Alliance partner");
        var target = new ComboBox<String>(); target.setPromptText("Knowledge to share");
        var share = new Button("Share research insights"); share.setDisable(true);
        if (playerEmpire != null) snapshot.empires().stream().filter(empire -> !playerEmpireId.equals(empire.id()))
                .filter(empire -> snapshot.diplomaticRelations().stream().anyMatch(relation ->
                        (playerEmpireId.equals(relation.empireAId()) && empire.id().equals(relation.empireBId())
                        || playerEmpireId.equals(relation.empireBId()) && empire.id().equals(relation.empireAId()))
                        && ("COMMERCIAL_ALLIANCE".equals(relation.tier()) || "INTEGRATED_FEDERATION".equals(relation.tier()))))
                .map(Empire::id).forEach(receiver.getItems()::add);
        receiver.valueProperty().addListener((observable, old, selected) -> {
            target.getItems().clear();
            if (selected != null && playerEmpire != null) playerEmpire.unlockedTechIds().stream()
                    .filter(id -> new ShareTechnologyCommand(playerEmpireId, selected, id).validate(snapshot)).forEach(target.getItems()::add);
        });
        target.valueProperty().addListener((observable, old, selected) -> share.setDisable(humanController == null || selected == null));
        share.setOnAction(event -> {
            humanController.stageCommand(new ShareTechnologyCommand(playerEmpireId, receiver.getValue(), target.getValue()));
            feedbackLabel.setText("Technology sharing queued. The recipient must fund its own research.");
        });
        return new HBox(8, receiver, target, share);
    }

    private VBox createReverseEngineeringSection() {
        VBox section = new VBox(8);
        section.setPadding(new Insets(10));
        section.setStyle("-fx-background-color: rgba(25, 40, 65, 0.65); -fx-background-radius: 8; -fx-border-color: #9b59b6; -fx-border-width: 1; -fx-border-radius: 8;");

        Text title = new Text("Xeno-debris and reverse engineering salvage");
        title.setFill(Color.VIOLET);
        title.setFont(Font.font("Verdana", FontWeight.BOLD, 14));
        section.getChildren().add(title);

        // Display progress of reverse engineering missions if any (mocked for now)
        Text missionProgress = new Text("Reverse engineering is unavailable until physical salvage ownership and consumption are implemented.");
        missionProgress.setFill(Color.LIGHTBLUE);
        missionProgress.setFont(Font.font("Verdana", 11));
        section.getChildren().add(missionProgress);

        HBox controls = new HBox(10);
        controls.setAlignment(Pos.CENTER_LEFT);

        Button deconstructBtn = new Button("Analyze wreckage debris (+progress vector)");
        deconstructBtn.setId("research-salvage");
        deconstructBtn.setDisable(true);
        deconstructBtn.setStyle("-fx-background-color: #8e44ad; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 11px;");

        Text desc = new Text("Reverse engineers recovered components accounting for biochemical translation distance.");
        desc.setFill(Color.LIGHTGRAY);
        desc.setFont(Font.font("Verdana", 11));

        controls.getChildren().addAll(deconstructBtn, desc);
        section.getChildren().add(controls);
        return section;
    }

    private VBox createTechBox(Technology tech) {
        VBox techBox = new VBox(6);
        techBox.setPadding(new Insets(10));
        techBox.setStyle("-fx-background-color: rgba(60, 80, 120, 0.5); -fx-background-radius: 5;");

        HBox titleRow = new HBox(10);
        titleRow.setAlignment(Pos.CENTER_LEFT);

        Text techName = new Text(tech.name());
        techName.setFill(Color.LIGHTBLUE);
        techName.setFont(Font.font("Verdana", FontWeight.BOLD, 16));

        Text techLevel = new Text("Level: 1"); // Placeholder for actual level
        techLevel.setFill(Color.LIGHTGOLDENRODYELLOW);
        techLevel.setFont(Font.font("Verdana", FontWeight.BOLD, 12));

        Text techComplexity = new Text("(Complexity: " + tech.complexity() + ")");
        techComplexity.setFill(Color.LIGHTSKYBLUE);
        techComplexity.setFont(Font.font("Verdana", 12));

        Spinner<Integer> scientistSpinner = new Spinner<>(0, (int) Math.min(Integer.MAX_VALUE, totalScientists), (int) Math.min(unassignedScientists, 5));
        scientistSpinner.setPrefWidth(80);
        scientistSpinner.setEditable(true);

        Button researchBtn = new Button("Start research");
        boolean researched = playerEmpire != null && playerEmpire.unlockedTechIds().contains(tech.id());
        researchBtn.setId("research-start-" + tech.id());
        researchBtn.setDisable(!researchAvailability(tech.id(), false).isEmpty() || humanController == null);
        if (researched) researchBtn.setText("Researched");
        researchBtn.setStyle("-fx-background-color: #2980b9; -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 11px;");
        researchBtn.setOnAction(e -> {
            int count = scientistSpinner.getValue();
            if (count <= 0) {
                feedbackLabel.setText("Cannot assign zero scientists.");
                feedbackLabel.setTextFill(Color.ORANGERED);
                return;
            }

            // Check against unassigned scientists
            // Note: if re-assigning to current project, we'd need to account for currently assigned
            // But this UI is for starting/restarting, let's keep it simple for now.
            if (count > unassignedScientists) {
                feedbackLabel.setText("Insufficient unassigned scientists available.");
                feedbackLabel.setTextFill(Color.ORANGERED);
                return;
            }

            if (humanController != null) {
                humanController.stageCommand(new StartResearchCommand(
                        playerEmpireId, tech.id(), false, count
                ));
                feedbackLabel.setText("Research queued for " + tech.name() + ". Assignment is validated on the next tick.");
                feedbackLabel.setTextFill(Color.LIGHTGREEN);
            }
        });

        titleRow.getChildren().addAll(techName, techLevel, techComplexity, scientistSpinner, researchBtn);

        Text techDesc = new Text(tech.description());
        techDesc.setFill(Color.WHITE);
        techDesc.setWrappingWidth(740);

        Text bonusText = new Text("Research cost: " + com.spaceconquest.engine.technology.ResearchPolicy.current()
                .cost(playerEmpire, tech.id(), false, technologies) + " points. Opens research for the applications below; each requires its own discovery.");
        bonusText.setFill(Color.LIGHTCYAN);
        bonusText.setFont(Font.font("Verdana", 10));

        techBox.getChildren().addAll(titleRow, techDesc, bonusText);
        techBox.getChildren().add(researchStatus(tech.id(), false));

        if (!tech.requiredTechnologies().isEmpty()) {
            Text reqs = new Text("Requires: " + String.join(", ", tech.requiredTechnologies()));
            reqs.setFill(Color.GOLD);
            reqs.setFont(Font.font("Verdana", 11));
            techBox.getChildren().add(reqs);
        }

        if (!tech.applications().isEmpty()) {
            VBox appsBox = new VBox(6);
            appsBox.setPadding(new Insets(5, 0, 0, 15));
            for (TechnicalApplication app : tech.applications()) {
                appsBox.getChildren().add(createAppBox(app));
            }
            techBox.getChildren().add(appsBox);
        }

        return techBox;
    }

    private VBox createAppBox(TechnicalApplication app) {
        VBox appBox = new VBox(4);
        appBox.setPadding(new Insets(6));
        appBox.setStyle("-fx-background-color: rgba(30, 45, 65, 0.4); -fx-background-radius: 4;");

        HBox appHeader = createAppHeader(app);

        Text appDesc = new Text(app.description());
        appDesc.setFill(Color.GAINSBORO);
        appDesc.setFont(Font.font("Verdana", 11));
        appDesc.setWrappingWidth(700);

        VBox optBox = createOptimizationBox(app);

        appBox.getChildren().addAll(appHeader, appDesc);
        Text benefits = new Text(ResearchBenefitText.describe(app, materials));
        benefits.setId("application-benefits-" + app.id()); benefits.setFill(Color.LIGHTGREEN);
        benefits.setFont(Font.font("Verdana", 11)); benefits.setWrappingWidth(700);
        appBox.getChildren().addAll(benefits, researchStatus(app.id(), true));
        var support = com.spaceconquest.engine.technology.ApplicationIntegration.describe(app);
        Text integration = new Text(support.status().name().charAt(0)
                + support.status().name().substring(1).toLowerCase(java.util.Locale.ROOT) + ": " + support.description());
        integration.setId("application-support-" + app.id());
        integration.setFill(support.status() == com.spaceconquest.engine.technology.ApplicationIntegration.Status.PLANNED
                ? Color.ORANGE : Color.LIGHTBLUE);
        integration.setFont(Font.font("Verdana", 10)); integration.setWrappingWidth(700);
        appBox.getChildren().add(integration);

        if (!app.requiredTechnologies().isEmpty()) {
            Text reqs = new Text("  Requires: " + String.join(", ", app.requiredTechnologies()));
            reqs.setFill(Color.ORANGE);
            reqs.setFont(Font.font("Verdana", 10));
            appBox.getChildren().add(reqs);
        }

        if (!app.affectedFactors().isEmpty()) {
            Text factors = new Text("  Design factors: " + String.join(", ", app.affectedFactors()));
            factors.setFill(Color.LIGHTCYAN);
            factors.setFont(Font.font("Verdana", 10));
            appBox.getChildren().add(factors);
        }

        if (!app.requiredMaterials().isEmpty()) {
            Text materials = new Text("  Materials: " + String.join(", ", app.requiredMaterials()));
            materials.setFill(Color.KHAKI);
            materials.setFont(Font.font("Verdana", 10));
            appBox.getChildren().add(materials);
        }

        appBox.getChildren().add(optBox);
        return appBox;
    }

    private HBox createAppHeader(TechnicalApplication app) {
        HBox appHeader = new HBox(10);
        appHeader.setAlignment(Pos.CENTER_LEFT);

        var modifiers = ApplicationProduction.modifiers(snapshot, playerEmpireId, app.id());
        Text appName = new Text("• " + app.name() + " (Cost: "
                + String.format("%.1f", app.calculateOptimizedUnitCost(modifiers))
                + " hrs, Complexity: " + app.calculateOptimizedComplexity(modifiers) + ")");
        appName.setFill(Color.LIGHTGREEN);
        appName.setFont(Font.font("Verdana", FontWeight.BOLD, 13));

        boolean researched = playerEmpire != null && playerEmpire.unlockedTechIds().contains(app.id());
        Text appBonus = new Text((researched ? "Refinement" : "Research") + " cost: "
                + com.spaceconquest.engine.technology.ResearchPolicy.current().cost(playerEmpire, app.id(), true, technologies) + " points");
        appBonus.setFill(Color.KHAKI);
        appBonus.setFont(Font.font("Verdana", 10));

        Spinner<Integer> appScientistSpinner = new Spinner<>(0, (int) Math.min(Integer.MAX_VALUE, totalScientists), (int) Math.min(unassignedScientists, 5));
        appScientistSpinner.setPrefWidth(80);
        appScientistSpinner.setEditable(true);

        var development = ApplicationRefinement.find(snapshot.applicationOptimizations(), playerEmpireId, app.id());
        Button appResearchBtn = new Button(researched ? "Refine app" : "Research app");
        appResearchBtn.setId("research-start-" + app.id());
        appResearchBtn.setDisable(humanController == null || !researchAvailability(app.id(), true).isEmpty());
        appResearchBtn.setStyle("-fx-background-color: #16a085; -fx-text-fill: white; -fx-font-size: 10px; -fx-font-weight: bold;");
        appResearchBtn.setOnAction(e -> {
            int count = appScientistSpinner.getValue();
            if (count <= 0) {
                feedbackLabel.setText("Cannot assign zero scientists.");
                feedbackLabel.setTextFill(Color.ORANGERED);
                return;
            }

            if (count > unassignedScientists) {
                feedbackLabel.setText("Insufficient unassigned scientists available.");
                feedbackLabel.setTextFill(Color.ORANGERED);
                return;
            }

            if (humanController != null) {
                humanController.stageCommand(new StartResearchCommand(
                        playerEmpireId, app.id(), true, count
                ));
                feedbackLabel.setText("Research queued for " + app.name() + ". Assignment is validated on the next tick.");
                feedbackLabel.setTextFill(Color.LIGHTGREEN);
            }
        });

        appHeader.getChildren().addAll(appName, appBonus, appScientistSpinner, appResearchBtn);
        return appHeader;
    }

    private VBox createOptimizationBox(TechnicalApplication app) {
        VBox optBox = new VBox(4);
        var development = ApplicationRefinement.find(snapshot.applicationOptimizations(), playerEmpireId, app.id());
        var modifiers = ApplicationProduction.modifiers(snapshot, playerEmpireId, app.id());
        String selectedPath = snapshot.applicationOptimizations().stream()
                .filter(selection -> playerEmpireId.equals(selection.empireId()) && app.id().equals(selection.applicationId()))
                .map(selection -> switch (selection.pathChoice()) {
                    case "PATH_A" -> "Performance";
                    case "PATH_B" -> "Miniaturization";
                    case "RESEARCH" -> "Research improvement";
                    default -> "None";
                })
                .findFirst().orElse("None");
        Text optHeader = new Text("Optimization: " + selectedPath
                + String.format(" | Output: %.0f%% | Material/work cost: %.0f%%",
                modifiers.effectMultiplier() * 100.0, modifiers.costMultiplier() * 100.0)
                + " | Refinement cycles: " + (development == null ? 0 : development.completedRefinements()));
        optHeader.setFill(Color.LIGHTCORAL);
        optHeader.setFont(Font.font("Verdana", FontWeight.BOLD, 9));

        HBox optControls = new HBox(10);
        optControls.setAlignment(Pos.CENTER_LEFT);

        var policy = com.spaceconquest.engine.technology.ResearchPolicy.current();
        Button pathABtn = new Button(optimizationLabel("Path A: performance", policy.performance()));
        boolean eligible = humanController != null && ApplicationProduction.canOptimize(playerEmpire, app.id(), technologies);
        eligible = eligible && (development == null || development.canChoosePath());
        pathABtn.setDisable(!eligible);
        pathABtn.setStyle("-fx-background-color: #2980b9; -fx-text-fill: white; -fx-font-size: 10px;");
        pathABtn.setOnAction(e -> {
            if (humanController != null) {
                humanController.stageCommand(new SelectOptimizationPathCommand(
                        playerEmpireId, app.id(), SelectOptimizationPathCommand.PATH_A_PERFORMANCE
                ));
                feedbackLabel.setText("Performance optimization queued for " + app.name() + ". Applies on the next tick.");
                feedbackLabel.setTextFill(Color.LIGHTGREEN);
            }
        });

        Button pathBBtn = new Button(optimizationLabel("Path B: miniaturize", policy.miniaturization()));
        pathBBtn.setDisable(!eligible);
        pathBBtn.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-font-size: 10px;");
        pathBBtn.setOnAction(e -> {
            if (humanController != null) {
                humanController.stageCommand(new SelectOptimizationPathCommand(
                        playerEmpireId, app.id(), SelectOptimizationPathCommand.PATH_B_MINIATURIZATION
                ));
                feedbackLabel.setText("Miniaturization optimization queued for " + app.name() + ". Applies on the next tick.");
                feedbackLabel.setTextFill(Color.LIGHTGREEN);
            }
        });

        optControls.getChildren().addAll(pathABtn, pathBBtn);
        optBox.getChildren().addAll(optHeader, optControls);
        if (development != null && development.pendingOutcome() != null)
            optBox.getChildren().add(createResearchDecisionControls(app, development.pendingOutcome()));
        return optBox;
    }

    private static String optimizationLabel(String name, ResearchVarianceResult result) {
        return name + String.format(" (%+.0f%% output, %+.0f%% cost, %+d complexity)",
                (result.effectMultiplier() - 1) * 100, (result.costMultiplier() - 1) * 100, result.complexityShift());
    }

    private HBox createResearchDecisionControls(TechnicalApplication app, ResearchVarianceResult outcome) {
        Label result = new Label(outcome.outcomeType().replace('_', ' ')
                + String.format(" | Output %.0f%% | Cost %.0f%% | Complexity %+d",
                outcome.effectMultiplier() * 100, outcome.costMultiplier() * 100, outcome.complexityShift()));
        result.setTextFill(Color.KHAKI);
        Button accept = new Button("Accept improvement");
        accept.setDisable(humanController == null
                || ResearchVarianceResult.OPTIMIZED_SUCCESS.equals(outcome.outcomeType()));
        accept.setOnAction(event -> {
            humanController.stageCommand(new ResolveApplicationResearchCommand(playerEmpireId, app.id(), true));
            feedbackLabel.setText("Research improvement queued for " + app.name() + ". Applies on the next tick.");
        });
        Button discard = new Button("Discard prototype");
        discard.setDisable(humanController == null);
        discard.setOnAction(event -> {
            humanController.stageCommand(new ResolveApplicationResearchCommand(playerEmpireId, app.id(), false));
            feedbackLabel.setText("Prototype discard queued for " + app.name() + ". Applies on the next tick.");
        });
        HBox controls = new HBox(10, result, accept, discard);
        controls.setAlignment(Pos.CENTER_LEFT);
        return controls;
    }
}
