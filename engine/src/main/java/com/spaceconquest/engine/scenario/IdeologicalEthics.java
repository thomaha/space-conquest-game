package com.spaceconquest.engine.scenario;

/**
 * Tracks ideological ethics alignment across four primary philosophical axes.
 * Values range from -2 to +2 (e.g. -2 = Fanatic Authoritarian, +2 = Fanatic Democratic).
 */
public record IdeologicalEthics(
        int authoritarianVsDemocratic,
        int militaristVsPacifist,
        int materialistVsSpiritualist,
        int xenophileVsXenophobe
) {
    public static final int MAX_ETHICS_POINTS = 3;

    public int totalPointsSpent() {
        return Math.abs(authoritarianVsDemocratic) +
                Math.abs(militaristVsPacifist) +
                Math.abs(materialistVsSpiritualist) +
                Math.abs(xenophileVsXenophobe);
    }

    public boolean isValid() {
        return totalPointsSpent() <= MAX_ETHICS_POINTS;
    }

    public static IdeologicalEthics balancedDefault() {
        return new IdeologicalEthics(1, 0, 1, 1);
    }

    public String getPrimaryIdeologySummary() {
        StringBuilder sb = new StringBuilder();
        if (authoritarianVsDemocratic > 0) sb.append(authoritarianVsDemocratic == 2 ? "Fanatic Democratic " : "Democratic ");
        else if (authoritarianVsDemocratic < 0) sb.append(authoritarianVsDemocratic == -2 ? "Fanatic Authoritarian " : "Authoritarian ");

        if (militaristVsPacifist > 0) sb.append(militaristVsPacifist == 2 ? "Fanatic Militarist " : "Militarist ");
        else if (militaristVsPacifist < 0) sb.append(militaristVsPacifist == -2 ? "Fanatic Pacifist " : "Pacifist ");

        if (materialistVsSpiritualist > 0) sb.append(materialistVsSpiritualist == 2 ? "Fanatic Materialist " : "Materialist ");
        else if (materialistVsSpiritualist < 0) sb.append(materialistVsSpiritualist == -2 ? "Fanatic Spiritualist " : "Spiritualist ");

        if (xenophileVsXenophobe > 0) sb.append(xenophileVsXenophobe == 2 ? "Fanatic Xenophile" : "Xenophile");
        else if (xenophileVsXenophobe < 0) sb.append(xenophileVsXenophobe == -2 ? "Fanatic Xenophobe" : "Xenophobe");

        String res = sb.toString().trim();
        return res.isEmpty() ? "Neutral Pragmatist" : res;
    }
}
