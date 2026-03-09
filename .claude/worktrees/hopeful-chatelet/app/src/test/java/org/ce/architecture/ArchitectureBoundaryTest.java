package org.ce.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Architecture guardrails for layered boundaries.
 */
class ArchitectureBoundaryTest {

    private static final Pattern IMPORT_PATTERN =
            Pattern.compile("^\\s*import\\s+([a-zA-Z0-9_\\.]+);\\s*$");

    private static final Path MAIN_SRC = Paths.get("src/main/java");

    @Test
    void domain_must_not_depend_on_outer_layers_except_allowlisted() throws IOException {
        Set<String> allowlist = Set.of();

        List<String> violations = findForbiddenImports(
                MAIN_SRC.resolve("org/ce/domain"),
                List.of(
                        "org.ce.application.",
                        "org.ce.infrastructure.",
                    "org.ce.presentation."
                ),
                allowlist
        );

        assertTrue(violations.isEmpty(), buildFailureMessage(
                "Domain boundary violation(s) found", violations));
    }

    @Test
    void application_should_not_depend_on_outer_adapters_except_allowlisted() throws IOException {
        Set<String> allowlist = Set.of();

        List<String> violations = findForbiddenImports(
                MAIN_SRC.resolve("org/ce/application"),
                List.of("org.ce.presentation."),
                allowlist
        );

        assertTrue(violations.isEmpty(), buildFailureMessage(
                "Application boundary violation(s) found", violations));
    }

    @Test
    void presentation_should_not_depend_on_legacy_workbench() throws IOException {
        Set<String> allowlist = Set.of();

        List<String> violations = findForbiddenImports(
            MAIN_SRC.resolve("org/ce/presentation"),
            List.of("org.ce.workbench."),
            allowlist
        );

        assertTrue(violations.isEmpty(), buildFailureMessage(
                "Presentation boundary violation(s) found", violations));
            }

    private static List<String> findForbiddenImports(
            Path root,
            List<String> forbiddenPrefixes,
            Set<String> allowlistedFiles) throws IOException {

        List<String> violations = new ArrayList<>();
        if (!Files.exists(root)) {
            return violations;
        }

        Set<String> normalizedAllowlist = new HashSet<>(allowlistedFiles);

        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(path -> path.toString().endsWith(".java"))
                    .forEach(file -> collectViolations(file, forbiddenPrefixes, normalizedAllowlist, violations));
        }

        return violations;
    }

    private static void collectViolations(
            Path file,
            List<String> forbiddenPrefixes,
            Set<String> allowlistedFiles,
            List<String> violations) {

        String relative = MAIN_SRC.relativize(file).toString().replace('\\', '/');

        try {
            List<String> lines = Files.readAllLines(file);
            for (String line : lines) {
                Matcher matcher = IMPORT_PATTERN.matcher(line);
                if (!matcher.matches()) {
                    continue;
                }

                String imported = matcher.group(1);
                boolean forbidden = forbiddenPrefixes.stream().anyMatch(imported::startsWith);

                if (forbidden && !allowlistedFiles.contains(relative)) {
                    violations.add(relative + " -> " + imported);
                }
            }
        } catch (IOException e) {
            violations.add(relative + " -> <unable to read file: " + e.getMessage() + ">");
        }
    }

    private static String buildFailureMessage(String header, List<String> violations) {
        StringBuilder sb = new StringBuilder(header).append("\n");
        sb.append("Existing allowed violations are temporary and explicitly listed in the test.\n");
        sb.append("New violations detected:\n");
        for (String violation : violations) {
            sb.append(" - ").append(violation).append('\n');
        }
        return sb.toString();
    }
}


