package com.example.config.obfuscator;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.DumperOptions;
import org.apache.commons.codec.digest.DigestUtils;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Config Obfuscator Tool
 * 
 * This tool obfuscates configuration keys in YAML files and updates client applications
 * to use the obfuscated keys.
 */
public class ConfigObfuscator {
    private final String projectRoot;
    private final String configRepo;
    private final String mappingFile;
    private Map<String, String> keyMapping;

    public ConfigObfuscator(String projectRoot) {
        this.projectRoot = projectRoot;
        this.configRepo = Paths.get(projectRoot, "config-repo").toString();
        this.mappingFile = Paths.get(projectRoot, "config-key-mapping.yaml").toString();
        this.keyMapping = new HashMap<>();
    }

    /**
     * Generate an obfuscated name for a configuration key.
     * Uses a hash-based approach to ensure consistency.
     */
    public String generateObfuscatedName(String originalKey) {
        // Create a hash of the original key
        String hexDigest = DigestUtils.md5Hex(originalKey);
        
        // Take first 8 characters and prefix with 'cfg_'
        String obfuscated = "cfg_" + hexDigest.substring(0, 8);
        return obfuscated;
    }

    /**
     * Recursively extract all keys from a nested map.
     */
    public List<String> extractKeysFromMap(Map<String, Object> data, String prefix) {
        List<String> keys = new ArrayList<>();
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            String fullKey = prefix.isEmpty() ? key : prefix + "." + key;
            keys.add(fullKey);
            
            if (value instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> nestedMap = (Map<String, Object>) value;
                keys.addAll(extractKeysFromMap(nestedMap, fullKey));
            }
        }
        return keys;
    }

    /**
     * Obfuscate keys in a YAML file and return the mapping and updated data.
     */
    public Map.Entry<Map<String, String>, Map<String, Object>> obfuscateYamlFile(String filePath) 
            throws IOException {
        Yaml yaml = new Yaml();
        List<Map<String, Object>> documents = new ArrayList<>();
        
        // Read all documents from the YAML file
        try (InputStream inputStream = Files.newInputStream(Paths.get(filePath))) {
            for (Object data : yaml.loadAll(inputStream)) {
                if (data != null) {
                    documents.add((Map<String, Object>) data);
                } else {
                    documents.add(null);
                }
            }
        }
        
        Map<String, String> keyMapping = new HashMap<>();
        List<Map<String, Object>> updatedDocuments = new ArrayList<>();
        
        for (Map<String, Object> doc : documents) {
            if (doc == null) {
                updatedDocuments.add(doc);
                continue;
            }
            
            // Process the document
            Map.Entry<Map<String, Object>, Map<String, String>> result = obfuscateDocument(doc);
            updatedDocuments.add(result.getKey());
            keyMapping.putAll(result.getValue());
        }
        
        // Write back the obfuscated content
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        Yaml outputYaml = new Yaml(options);
        
        try (FileWriter writer = new FileWriter(filePath)) {
            for (Map<String, Object> doc : updatedDocuments) {
                outputYaml.dump(doc, writer);
                if (updatedDocuments.size() > 1 && doc != updatedDocuments.get(updatedDocuments.size() - 1)) {
                    writer.write("---\n");
                }
            }
        }
        
        return new AbstractMap.SimpleEntry<>(keyMapping, 
            updatedDocuments.isEmpty() ? new HashMap<>() : updatedDocuments.get(0));
    }

    /**
     * Obfuscate keys in a single YAML document.
     */
    public Map.Entry<Map<String, Object>, Map<String, String>> obfuscateDocument(Map<String, Object> data) {
        if (data == null) {
            return new AbstractMap.SimpleEntry<>(data, new HashMap<>());
        }
        
        Map<String, String> keyMapping = new HashMap<>();
        Map<String, Object> updatedData = new HashMap<>();
        
        // Special handling for profile documents
        if (data.containsKey("spring") && 
            data.get("spring") instanceof Map &&
            ((Map<?, ?>) data.get("spring")).containsKey("profiles")) {
            // This is a profile-specific section
            Map<String, Object> profileData = new HashMap<>(data);
            if (profileData.containsKey("configurations")) {
                Map.Entry<Map<String, Object>, Map<String, String>> result = 
                    obfuscateConfigurations((Map<String, Object>) profileData.get("configurations"));
                profileData.put("configurations", result.getKey());
                keyMapping.putAll(result.getValue());
            }
            return new AbstractMap.SimpleEntry<>(profileData, keyMapping);
        } else if (data.containsKey("configurations")) {
            // This is the main configuration section
            Map.Entry<Map<String, Object>, Map<String, String>> result = 
                obfuscateConfigurations((Map<String, Object>) data.get("configurations"));
            data.put("configurations", result.getKey());
            keyMapping.putAll(result.getValue());
            return new AbstractMap.SimpleEntry<>(data, keyMapping);
        } else {
            // Regular document, process recursively
            for (Map.Entry<String, Object> entry : data.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();
                if (value instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map.Entry<Map<String, Object>, Map<String, String>> result = 
                        obfuscateDocument((Map<String, Object>) value);
                    updatedData.put(key, result.getKey());
                    keyMapping.putAll(result.getValue());
                } else {
                    updatedData.put(key, value);
                }
            }
            return new AbstractMap.SimpleEntry<>(updatedData, keyMapping);
        }
    }

    /**
     * Obfuscate the configurations section.
     */
    public Map.Entry<Map<String, Object>, Map<String, String>> obfuscateConfigurations(
            Map<String, Object> configData) {
        Map<String, String> keyMapping = new HashMap<>();
        Map<String, Object> obfuscatedConfig = new HashMap<>();
        
        for (Map.Entry<String, Object> entry : configData.entrySet()) {
            String sectionKey = entry.getKey();
            Object sectionValue = entry.getValue();
            
            String obfuscatedSectionKey = generateObfuscatedName(sectionKey);
            // Only map original keys to obfuscated keys, not values
            keyMapping.put(sectionKey, obfuscatedSectionKey);
            
            if (sectionValue instanceof Map) {
                @SuppressWarnings("unchecked")
                Map.Entry<Map<String, Object>, Map<String, String>> result = 
                    obfuscateSection((Map<String, Object>) sectionValue, sectionKey + ".");
                // Merge mappings, but only key mappings
                keyMapping.putAll(result.getValue());
                obfuscatedConfig.put(obfuscatedSectionKey, result.getKey());
            } else {
                // Do not obfuscate values, only keys
                obfuscatedConfig.put(obfuscatedSectionKey, sectionValue);
            }
        }
        
        return new AbstractMap.SimpleEntry<>(obfuscatedConfig, keyMapping);
    }

    /**
     * Obfuscate a configuration section (like app, database, etc.).
     */
    public Map.Entry<Map<String, Object>, Map<String, String>> obfuscateSection(
            Map<String, Object> sectionData, String prefix) {
        Map<String, String> keyMapping = new HashMap<>();
        Map<String, Object> obfuscatedSection = new HashMap<>();
        
        for (Map.Entry<String, Object> entry : sectionData.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            
            String fullKey = prefix + key;
            String obfuscatedKey = generateObfuscatedName(key);
            // Only map original keys to obfuscated keys, not values
            keyMapping.put(key, obfuscatedKey);
            
            if (value instanceof Map) {
                // Handle nested dictionaries
                @SuppressWarnings("unchecked")
                Map.Entry<Map<String, Object>, Map<String, String>> result = 
                    obfuscateSection((Map<String, Object>) value, fullKey + ".");
                // Merge mappings, but only key mappings
                keyMapping.putAll(result.getValue());
                obfuscatedSection.put(obfuscatedKey, result.getKey());
            } else {
                // Do not obfuscate values, only keys
                obfuscatedSection.put(obfuscatedKey, value);
            }
        }
        
        return new AbstractMap.SimpleEntry<>(obfuscatedSection, keyMapping);
    }

    /**
     * Process all configuration files in the config-repo directory.
     * Create separate mapping files for each configuration directory found.
     */
    public Map<String, List<String>> processAllConfigFiles() throws IOException {
        // First, collect all configuration files by their parent directory
        Map<String, List<String>> configDirs = new HashMap<>();
        
        // Walk through the config-repo directory
        Files.walk(Paths.get(configRepo))
            .filter(Files::isRegularFile)
            .filter(path -> {
                String fileName = path.getFileName().toString();
                return fileName.endsWith("runtime-config.yml") || fileName.endsWith("runtime-config.yaml");
            })
            .forEach(path -> {
                String parentDir = path.getParent().getFileName().toString();
                
                // If file is directly in config-repo, use file name without extension
                if (parentDir.equals("config-repo")) {
                    String fileName = path.getFileName().toString();
                    parentDir = fileName.replace("-runtime-config.yml", "")
                                       .replace("-runtime-config.yaml", "");
                }
                
                configDirs.computeIfAbsent(parentDir, k -> new ArrayList<>()).add(path.toString());
            });
        
        // Process each configuration directory separately
        for (Map.Entry<String, List<String>> entry : configDirs.entrySet()) {
            String configDirName = entry.getKey();
            List<String> files = entry.getValue();
            
            System.out.println("Processing configuration directory: " + configDirName);
            Map<String, String> projectMappings = new HashMap<>();
            
            for (String filePath : files) {
                System.out.println("  Processing " + filePath);
                try {
                    Map.Entry<Map<String, String>, Map<String, Object>> result = obfuscateYamlFile(filePath);
                    Map<String, String> mapping = result.getKey();
                    projectMappings.putAll(mapping);
                    System.out.println("    Obfuscated " + mapping.size() + " keys");
                } catch (Exception e) {
                    System.out.println("    Error processing " + filePath + ": " + e.getMessage());
                }
            }
            
            // Save directory-specific mapping
            String projectMappingFile = Paths.get(projectRoot, configDirName + "-key-mapping.yaml").toString();
            saveMapping(projectMappings, projectMappingFile);
            System.out.println("  Key mapping saved to " + projectMappingFile);
        }
        
        return configDirs;
    }

    /**
     * Save the key mapping to a file.
     */
    public void saveMapping(Map<String, String> mapping, String mappingFile) throws IOException {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        Yaml yaml = new Yaml(options);
        
        try (FileWriter writer = new FileWriter(mappingFile)) {
            yaml.dump(mapping, writer);
        }
        System.out.println("Key mapping saved to " + mappingFile);
    }

    /**
     * Main method to run the obfuscation process.
     */
    public static void main(String[] args) {
        String projectRoot = System.getProperty("user.dir");
        ConfigObfuscator obfuscator = new ConfigObfuscator(projectRoot);
        
        System.out.println("Starting configuration obfuscation...");
        System.out.println("Processing all configuration files...");
        try {
            obfuscator.processAllConfigFiles();
            System.out.println("Configuration obfuscation complete.");
        } catch (IOException e) {
            System.err.println("Error during obfuscation: " + e.getMessage());
            e.printStackTrace();
        }
    }
}