import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import org.yaml.snakeyaml.Yaml;

public class ConfigRefactor {
    private final String projectRoot;
    private final String mappingFile;
    private Map<String, String> keyMapping;

    public ConfigRefactor(String projectRoot) {
        this.projectRoot = projectRoot;
        this.mappingFile = projectRoot + File.separator + "config-key-mapping.yaml";
        this.keyMapping = loadMapping();
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> loadMapping() {
        try (InputStream in = Files.newInputStream(Paths.get(mappingFile))) {
            Yaml yaml = new Yaml();
            Object loaded = yaml.load(in);
            if (loaded instanceof Map) {
                return (Map<String, String>) loaded;
            }
        } catch (IOException e) {
            System.out.println("Mapping file not found: " + mappingFile);
        }
        return new HashMap<>();
    }

    public void updateJavaFile(Path filePath) throws IOException {
        String content = Files.readString(filePath);
        String updatedContent = content;

        for (Map.Entry<String, String> entry : keyMapping.entrySet()) {
            String original = entry.getKey();
            String obfuscated = entry.getValue();
            if (!original.equals(obfuscated)) {
                // örnek: private Type original;
                updatedContent = updatedContent.replaceAll(
                    "(private\\s+\\w+\\s+)" + Pattern.quote(original) + "(\\s*;)",
                    "$1" + obfuscated + "$2"
                );
                // getter
                updatedContent = updatedContent.replaceAll(
                    "(get)" + Pattern.quote(capitalize(original)) + "(\\s*\\()",
                    "$1" + capitalize(obfuscated) + "$2"
                );
                // setter
                updatedContent = updatedContent.replaceAll(
                    "(set)" + Pattern.quote(capitalize(original)) + "(\\s*\\()",
                    "$1" + capitalize(obfuscated) + "$2"
                );
            }
        }

        if (!updatedContent.equals(content)) {
            Files.writeString(filePath, updatedContent);
            System.out.println("Updated " + filePath);
        }
    }

    public void processAllProjects() throws IOException {
        try (var dirs = Files.list(Paths.get(projectRoot))) {
            dirs.filter(Files::isDirectory).forEach(project -> {
                Path srcDir = project.resolve("src");
                if (Files.exists(srcDir)) {
                    try {
                        Files.walk(srcDir)
                            .filter(p -> p.toString().endsWith(".java"))
                            .forEach(p -> {
                                try {
                                    updateJavaFile(p);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            });
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            });
        }
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0,1).toUpperCase() + s.substring(1);
    }

    public static void main(String[] args) throws Exception {
        String projectRoot = new File(".").getAbsolutePath();
        ConfigRefactor refactor = new ConfigRefactor(projectRoot);
        refactor.processAllProjects();
    }
}
