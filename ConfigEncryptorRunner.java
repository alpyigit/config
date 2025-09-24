package com.example.configencryptor.runner;

import com.example.configencryptor.service.ConfigFileProcessor;
import com.example.configencryptor.service.EncryptionService;
import com.example.configencryptor.service.JavaFileUpdaterService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;

@Component
public class ConfigEncryptorRunner implements CommandLineRunner {

    @Autowired
    private ConfigFileProcessor configFileProcessor;
    
    @Autowired
    private EncryptionService encryptionService;
    
    @Autowired
    private JavaFileUpdaterService javaFileUpdaterService;

    // Default paths - can be overridden with command line arguments
    private static final String DEFAULT_SOURCE_DIR = "c:/Users/YTIRPAN/Documents/config_encryptor/config-repo";
    private static final String DEFAULT_TARGET_DIR = "c:/Users/YTIRPAN/Documents/config_encryptor/config-repo-encrypted";

    @Override
    public void run(String... args) throws Exception {
        System.out.println("=".repeat(60));
        System.out.println("        CONFIG REPOSITORY ENCRYPTOR");
        System.out.println("=".repeat(60));
        
        String sourceDir = DEFAULT_SOURCE_DIR;
        String targetDir = DEFAULT_TARGET_DIR;
        
        // Parse named arguments
        for (String arg : args) {
            if (arg.startsWith("--source.dir=")) {
                sourceDir = arg.substring("--source.dir=".length());
            } else if (arg.startsWith("--target.dir=")) {
                targetDir = arg.substring("--target.dir=".length());
            }
        }
        
        // Fallback to positional arguments if no named arguments found
        if (args.length > 0 && !args[0].startsWith("--")) {
            sourceDir = args[0];
            if (args.length > 1) {
                targetDir = args[1];
            }
        }

        System.out.println("Source directory: " + sourceDir);
        System.out.println("Target directory: " + targetDir);
        System.out.println();
        
        // Check Config Server connectivity
        System.out.println("🔗 Checking Config Server connectivity...");
        System.out.println("Config Server URL: " + encryptionService.getConfigServerUrl());
        
        if (!encryptionService.isConfigServerAvailable()) {
            System.err.println("❌ ERROR: Cannot connect to Config Server at " + encryptionService.getConfigServerUrl());
            System.err.println("Please ensure the Config Server is running before starting the encryption process.");
            System.err.println("You can start the Config Server with: mvn spring-boot:run (in unified-config-server directory)");
            return;
        }
        
        System.out.println("✅ Config Server is available and ready for encryption!");
        System.out.println();

        // Validate source directory exists
        if (!Files.exists(Paths.get(sourceDir))) {
            System.err.println("❌ ERROR: Source directory does not exist: " + sourceDir);
            return;
        }

        try {
            // Process all configuration files
            configFileProcessor.processConfigRepository(sourceDir, targetDir);
            
            // Test decryption with a sample encrypted value
            System.out.println();
            System.out.println("🔍 Testing Config Server decrypt functionality...");
            testDecryption();
            
            System.out.println();
            System.out.println("🔄 Updating client Java files with shuffled keys...");
            updateClientJavaFiles(sourceDir, targetDir);
            
            System.out.println();
            System.out.println("✅ Configuration encryption completed successfully!");
            System.out.println("📁 Encrypted files are saved in: " + targetDir);
            System.out.println();
            System.out.println("Next steps:");
            System.out.println("1. Review the encrypted configuration files");
            System.out.println("2. Update your Config Server to use the encrypted repository");
            System.out.println("3. Ensure your Config Server has the correct encryption key");
            
        } catch (IOException e) {
            System.err.println("❌ ERROR: Failed to process configuration files: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void testDecryption() {
        try {
            // Test with a simple value
            String testValue = "TestValue123";
            String encrypted = encryptionService.encrypt(testValue);
            String decrypted = encryptionService.decrypt(encrypted);
            
            System.out.println("Original: " + testValue);
            System.out.println("Encrypted: " + encrypted);
            System.out.println("Decrypted: " + decrypted);
            
            if (testValue.equals(decrypted)) {
                System.out.println("✅ Encryption/Decryption test PASSED!");
            } else {
                System.out.println("❌ Encryption/Decryption test FAILED!");
            }
        } catch (Exception e) {
            System.out.println("❌ Decryption test failed: " + e.getMessage());
        }
    }
    
    private void updateClientJavaFiles(String sourceDir, String targetDir) {
        try {
            // Load key mapping from the new mapping directory
            String mappingFilePath = Paths.get(sourceDir).getParent().resolve("config-repo-key-mappings").resolve("key-mapping.yml").toString();
            Map<String, String> keyMapping = javaFileUpdaterService.loadKeyMapping(mappingFilePath);
            
            if (keyMapping.isEmpty()) {
                System.out.println("⚠️ No key mapping found, skipping Java file updates");
                return;
            }
            
            // Update client Java files with shuffled keys
            javaFileUpdaterService.updateClientJavaFilesWithShuffledKeys(keyMapping, sourceDir);
            
            System.out.println("✅ Java file updates completed!");
        } catch (Exception e) {
            System.err.println("❌ ERROR: Failed to update client Java files: " + e.getMessage());
            e.printStackTrace();
        }
    }
}