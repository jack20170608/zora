package top.ilovemyhome.zora.rdb.flyway;

import liquibase.Contexts;
import liquibase.Liquibase;
import liquibase.changelog.ChangeSet;
import liquibase.changelog.DatabaseChangeLog;
import liquibase.change.core.RawSQLChange;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.LiquibaseException;
import liquibase.resource.ClassLoaderResourceAccessor;
import liquibase.resource.DirectoryResourceAccessor;
import liquibase.resource.Resource;
import liquibase.resource.ResourceAccessor;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Runs existing V&lt;version&gt;__&lt;description&gt;.sql scripts with Liquibase history tracking.
 * This is a new migration history format; Flyway's history is not imported.
 */
public final class SqlMigrationEngine {
    private static final Pattern SCRIPT = Pattern.compile("V([0-9][0-9._]*)__(.+)\\.sql");
    private final DataSource dataSource;
    private final String[] locations;
    private final String targetVersion;
    private final String table;
    private final String schema;
    private final Map<String, String> placeholders;
    private final boolean placeholderReplacement;
    private final boolean cleanDisabled;

    public SqlMigrationEngine(DataSource dataSource, String[] locations, String targetVersion, String table,
                              String schema, Map<String, String> placeholders, boolean placeholderReplacement,
                              boolean cleanDisabled) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.locations = locations.clone();
        this.targetVersion = targetVersion;
        this.table = table;
        this.schema = schema;
        this.placeholders = new HashMap<>(placeholders);
        this.placeholderReplacement = placeholderReplacement;
        this.cleanDisabled = cleanDisabled;
    }

    public int pendingCount() {
        return run(liquibase -> liquibase.listUnrunChangeSets(new Contexts()).size());
    }

    public int migrate() {
        return run(liquibase -> {
            int pending = liquibase.listUnrunChangeSets(new Contexts()).size();
            liquibase.update();
            return pending;
        });
    }

    public void validate() {
        run(liquibase -> { liquibase.getChangeSetStatuses(new Contexts()); return null; });
    }

    public void clean() {
        if (cleanDisabled) {
            throw new IllegalStateException("Database clean is disabled");
        }
        run(liquibase -> { liquibase.dropAll(); return null; });
    }

    public void baseline() {
        run(liquibase -> { liquibase.changeLogSync(new Contexts()); return null; });
    }

    public void repair() {
        run(liquibase -> { liquibase.clearCheckSums(); return null; });
    }

    public List<MigrationStatus> info() {
        return run(liquibase -> liquibase.getChangeSetStatuses(new Contexts()).stream()
            .map(status -> new MigrationStatus(status.getChangeSet().getId(),
                status.getChangeSet().getComments(), status.getPreviouslyRan())).toList());
    }

    public record MigrationStatus(String version, String description, boolean applied) { }

    private <T> T run(Operation<T> operation) {
        try (Connection connection = dataSource.getConnection()) {
            Database database = DatabaseFactory.getInstance()
                .findCorrectDatabaseImplementation(new JdbcConnection(connection));
            if (table != null) database.setDatabaseChangeLogTableName(table);
            if (schema != null) database.setDefaultSchemaName(schema);
            try (ResourceAccessor accessor = accessor()) {
                DatabaseChangeLog changeLog = changeLog(accessor);
                Liquibase liquibase = new Liquibase(changeLog, accessor, database);
                if (placeholderReplacement) {
                    placeholders.forEach((key, value) -> liquibase.setChangeLogParameter(key, value));
                }
                return operation.execute(liquibase);
            }
        } catch (Exception e) {
            throw new IllegalStateException("Database migration operation failed", e);
        }
    }

    private ResourceAccessor accessor() throws IOException {
        List<ResourceAccessor> accessors = new ArrayList<>();
        for (String location : locations) {
            if (location.startsWith("classpath:")) {
                accessors.add(new ClassLoaderResourceAccessor());
            } else if (location.startsWith("filesystem:")) {
                accessors.add(new DirectoryResourceAccessor(Path.of(location.substring("filesystem:".length()))));
            } else {
                throw new IllegalArgumentException("Unsupported migration location: " + location);
            }
        }
        return new liquibase.resource.CompositeResourceAccessor(accessors.toArray(ResourceAccessor[]::new));
    }

    private DatabaseChangeLog changeLog(ResourceAccessor accessor) throws IOException {
        DatabaseChangeLog changeLog = new DatabaseChangeLog("zora-sql-migrations:" + String.join(",", locations));
        List<Script> scripts = new ArrayList<>();
        for (String location : locations) {
            String directory = location.startsWith("classpath:") ? location.substring("classpath:".length()) : "";
            for (Resource resource : accessor.search(directory, false)) {
                String filename = Path.of(resource.getPath()).getFileName().toString();
                Matcher matcher = SCRIPT.matcher(filename);
                if (matcher.matches() && (targetVersion == null || compareVersions(matcher.group(1), targetVersion) <= 0)) {
                    scripts.add(new Script(matcher.group(1), matcher.group(2).replace('_', ' '), resource));
                }
            }
        }
        scripts.sort(Comparator.comparing(Script::version, SqlMigrationEngine::compareVersions));
        for (Script script : scripts) {
            ChangeSet changeSet = new ChangeSet(script.version(), "zora", false, false,
                changeLog.getFilePath(), null, null, changeLog);
            changeSet.setComments(script.description());
            String content;
            try (InputStream input = script.resource().openInputStream()) {
                content = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            }
            if (placeholderReplacement) {
                for (Map.Entry<String, String> entry : placeholders.entrySet()) {
                    content = content.replace("${" + entry.getKey() + "}", entry.getValue());
                }
            }
            RawSQLChange sql = new RawSQLChange();
            sql.setSql(content);
            changeSet.addChange(sql);
            changeLog.addChangeSet(changeSet);
        }
        return changeLog;
    }

    private static int compareVersions(String first, String second) {
        String[] left = first.replace('_', '.').split("\\.");
        String[] right = second.replace('_', '.').split("\\.");
        for (int index = 0; index < Math.max(left.length, right.length); index++) {
            long a = index < left.length ? Long.parseLong(left[index]) : 0;
            long b = index < right.length ? Long.parseLong(right[index]) : 0;
            if (a != b) return Long.compare(a, b);
        }
        return 0;
    }

    private record Script(String version, String description, Resource resource) { }

    @FunctionalInterface
    private interface Operation<T> {
        T execute(Liquibase liquibase) throws LiquibaseException;
    }
}
