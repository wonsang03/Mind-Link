package com.mindlink.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 관리자 전용 SQL 콘솔. SELECT/WITH 는 결과 행을, 그 외(DML/DDL)는 영향받은 행 수를 돌려줍니다.
 * 임의 SQL 실행이므로 컨트롤러에서 반드시 ADMIN 권한을 확인한 뒤에만 호출합니다.
 */
@Service
public class SqlConsoleService {

    /** 한 번에 반환하는 최대 행 수(과도한 출력 방지). */
    private static final int MAX_ROWS = 500;

    private final JdbcTemplate jdbcTemplate;

    public SqlConsoleService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public record SqlResult(
            boolean query,
            List<String> columns,
            List<List<Object>> rows,
            boolean truncated,
            int updateCount,
            String error
    ) {
        static SqlResult error(String message) {
            return new SqlResult(false, List.of(), List.of(), false, 0, message);
        }
    }

    public SqlResult execute(String sql) {
        if (sql == null || sql.isBlank()) {
            return SqlResult.error("실행할 SQL을 입력하세요.");
        }
        String trimmed = sql.strip();
        // 끝의 세미콜론은 단일 문 실행에 방해가 되므로 제거
        while (trimmed.endsWith(";")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1).strip();
        }
        String head = trimmed.toLowerCase(Locale.ROOT);
        boolean isQuery = head.startsWith("select") || head.startsWith("with");

        try {
            if (isQuery) {
                return runQuery(trimmed);
            }
            int affected = jdbcTemplate.update(trimmed);
            return new SqlResult(false, List.of(), List.of(), false, affected, null);
        } catch (Exception e) {
            Throwable root = e;
            while (root.getCause() != null && root.getCause() != root) {
                root = root.getCause();
            }
            return SqlResult.error(root.getMessage() == null ? e.toString() : root.getMessage());
        }
    }

    private SqlResult runQuery(String sql) {
        return jdbcTemplate.query(sql, rs -> {
            ResultSetMetaData meta = rs.getMetaData();
            int columnCount = meta.getColumnCount();
            List<String> columns = new ArrayList<>(columnCount);
            for (int i = 1; i <= columnCount; i++) {
                columns.add(meta.getColumnLabel(i));
            }
            List<List<Object>> rows = new ArrayList<>();
            boolean truncated = false;
            while (rs.next()) {
                if (rows.size() >= MAX_ROWS) {
                    truncated = true;
                    break;
                }
                List<Object> row = new ArrayList<>(columnCount);
                for (int i = 1; i <= columnCount; i++) {
                    Object value = rs.getObject(i);
                    row.add(value == null ? null : String.valueOf(value));
                }
                rows.add(row);
            }
            return new SqlResult(true, columns, rows, truncated, 0, null);
        });
    }
}
