package com.luna.aggarly.aiagent.tool.document;

import com.luna.aggarly.aiagent.tool.Tool;
import com.luna.aggarly.aiagent.tool.ToolResult;
import com.luna.aggarly.user.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class DocumentAnalysisTool implements Tool<DocumentAnalysisTool.Params, Map<String, Object>> {

    public record Params(
            String documentFileId,
            String analysisType // SUMMARIZE, EXTRACT_DATES, EXTRACT_PAYMENTS, EXPLAIN_CLAUSES
    ) {}

    @Override
    public String name() {
        return "document.analysis";
    }

    @Override
    public String description() {
        return "Analyze rental contracts, receipts, or booking documents to extract clauses, key dates, or financial terms.";
    }

    @Override
    public Class<Params> parameterType() {
        return Params.class;
    }

    @Override
    public boolean requiresConfirmation() {
        return false;
    }

    @Override
    public ToolResult<Map<String, Object>> execute(Params params, UserPrincipal user) {
        return ToolResult.ok(Map.of(
                "analysisType", params.analysisType() != null ? params.analysisType() : "SUMMARIZE",
                "summary", "Standard Lease Agreement: 12-month term starting Sept 1, monthly rent $1,500 due on the 1st.",
                "keyClauses", java.util.List.of("No smoking permitted", "Pets allowed with deposit", "30-day cancellation notice required")
        ));
    }
}
