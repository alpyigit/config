import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import org.yaml.snakeyaml.Yaml;

public class ConfigObfuscator {
    private final String projectRoot;
    private final String configRepo;
    private final String mappingFile;
    private final Map<String, String> keyMapping = new HashMap<>();

    public ConfigObfuscator(String projectRoot) {
        this.projectRoot = projectRoot;
        this.configRepo = projectRoot + File.separator + "config-repo";
        this.mappingFile = projectRoot + File.separator + "config-key-mapping.yaml";
    }

    public String generateObfuscatedName(String originalKey) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(originalKey.getBytes());
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 4; i++) {
                sb.append(String.format("%02x", digest[i]));
            }
            return "cfg_" + sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("Error generating hash", e);
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, String> obfuscateYamlFile(Path filePath) throws IOException {
        Yaml yaml = new Yaml();
        List<Object> documents;
        try (InputStream in = Files.newInputStream(filePath)) {
            documents = new ArrayList<>();
            yaml.loadAll(in).forEach(documents::add);
        }

        Map<String, String> mapping = new HashMap<>();
        List<Object> updatedDocs = new ArrayList<>();

        for (Object doc : documents) {
            if (doc instanceof Map) {
                Map<String, Object> updated = new LinkedHashMap<>();
                Map<String, String> docMapping = obfuscateDocument((Map<String, Object>) doc, updated);
                mapping.putAll(docMapping);
                updatedDocs.add(updated);
            } else {
                updatedDocs.add(doc);
            }
        }

        try (Writer writer = Files.newBufferedWriter(filePath)) {
            yaml.dumpAll(updatedDocs.iterator(), writer);
        }
        return mapping;
    }

    private Map<String, String> obfuscateDocument(Map<String, Object> data, Map<String, Object> out) {
        Map<String, String> mapping = new HashMap<>();
        for (Map.Entry<String, Object> entry : data.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            String obfuscatedKey = generateObfuscatedName(key);
            mapping.put(key, obfuscatedKey);

            if (value instanceof Map) {
                Map<String, Object> nestedOut = new LinkedHashMap<>();
                mapping.putAll(obfuscateDocument((Map<String, Object>) value, nestedOut));
                out.put(obfuscatedKey, nestedOut);
            } else {
                out.put(obfuscatedKey, value);
            }
        }
        return mapping;
    }

    public void saveMapping(Map<String, String> mapping, String filePath) throws IOException {
        Yaml yaml = new Yaml();
        try (Writer writer = Files.newBufferedWriter(Paths.get(filePath))) {
            yaml.dump(mapping, writer);
        }
        System.out.println("Key mapping saved to " + filePath);
    }

    public static void main(String[] args) throws Exception {
        String projectRoot = new File(".").getAbsolutePath();
        ConfigObfuscator obfuscator = new ConfigObfuscator(projectRoot);

        System.out.println("Starting configuration obfuscation...");
        try (var walk = Files.walk(Paths.get(obfuscator.configRepo))) {
            walk.filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith("runtime-config.yml") || p.toString().endsWith("runtime-config.yaml"))
                .forEach(p -> {
                    try {
                        Map<String, String> mapping = obfuscator.obfuscateYamlFile(p);
                        obfuscator.saveMapping(mapping, obfuscator.mappingFile);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });
        }
        System.out.println("Configuration obfuscation complete.");
    }
}
