package com.alves_dev.sos.config;

import com.alves_dev.sos.service.ClientManagementService;
import com.alves_dev.sos.service.V2MigrationService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class CliCommandRunner implements ApplicationRunner {

    private final ClientManagementService clientManagementService;
    private final V2MigrationService migrationService;
    private final ConfigurableApplicationContext applicationContext;

    public CliCommandRunner(ClientManagementService clientManagementService,
                            V2MigrationService migrationService,
                            ConfigurableApplicationContext applicationContext) {
        this.clientManagementService = clientManagementService;
        this.migrationService = migrationService;
        this.applicationContext = applicationContext;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<String> command = args.getNonOptionArgs();
        if (command.isEmpty()) {
            return;
        }

        switch (command.getFirst()) {
            case "create-client" -> createClient(command, args.containsOption("admin"));
            case "migrate-v2" -> migrate(args.containsOption("dry-run"));
            default -> {
                return;
            }
        }
        applicationContext.close();
    }

    private void createClient(List<String> command, boolean admin) {
        if (command.size() != 3) {
            throw new IllegalArgumentException(
                    "Usage: create-client <clientId> <name> [--admin]");
        }
        var created = clientManagementService.create(command.get(1), command.get(2), admin);
        System.out.println("Client created: " + created.clientId());
        System.out.println("API key: " + created.apiKey());
    }

    private void migrate(boolean dryRun) {
        var report = migrationService.migrate(dryRun);
        System.out.println(dryRun ? "V2 migration dry run" : "V2 migration completed");
        System.out.println("Files analyzed: " + report.filesAnalyzed());
        System.out.println("Buckets to create: " + report.bucketsToCreate());
        System.out.println("Filenames to derive: " + report.filenamesToDerive());
        System.out.println("Duplicates found: " + report.duplicatesFound());
        System.out.println("Invalid documents: " + report.invalidDocuments());
        System.out.println("Missing physical files: " + report.missingPhysicalFiles());
        if (report.createdAdminApiKey() != null) {
            System.out.println("Client created: developer-admin");
            System.out.println("API key: " + report.createdAdminApiKey());
        }
    }
}
