package com.herbscript.agent.tool.tools;

import com.herbscript.agent.tool.AgentTool;
import com.herbscript.agent.tool.dto.ToolExecutionRequest;
import com.herbscript.agent.tool.dto.ToolExecutionResult;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class GetCommonHerbsTool implements AgentTool {

    private final JdbcTemplate jdbcTemplate;

    public GetCommonHerbsTool(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public String name() {
        return "get_common_herbs";
    }

    @Override
    public String description() {
        return "统计患者历史处方中的高频药味";
    }

    @Override
    public ToolExecutionResult execute(ToolExecutionRequest request) {
        long start = System.currentTimeMillis();
        Long patientId = ((Number) request.arguments().get("patientId")).longValue();
        int limit = request.arguments().get("limit") instanceof Number number ? number.intValue() : 10;
        List<Map<String, Object>> payload = jdbcTemplate.query(
                """
                SELECT pi.herb_name, COUNT(*) AS herb_count
                FROM prescription_item pi
                INNER JOIN prescription p ON p.id = pi.prescription_id
                WHERE p.deleted = 0 AND p.patient_id = ?
                GROUP BY pi.herb_name
                ORDER BY herb_count DESC, pi.herb_name ASC
                LIMIT ?
                """,
                (rs, rowNum) -> Map.of(
                        "herbName", rs.getString("herb_name"),
                        "count", rs.getInt("herb_count")
                ),
                patientId,
                limit
        );
        return new ToolExecutionResult(name(), "success", payload, null, (int) (System.currentTimeMillis() - start));
    }
}
