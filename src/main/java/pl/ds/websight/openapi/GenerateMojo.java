package pl.ds.websight.openapi;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Schema;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.jetbrains.annotations.NotNull;
import org.reflections.Reflections;
import org.reflections.scanners.ResourcesScanner;
import org.reflections.scanners.SubTypesScanner;
import org.reflections.scanners.TypeAnnotationsScanner;
import org.reflections.util.ConfigurationBuilder;
import pl.ds.websight.rest.framework.annotations.SlingAction;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Set;

import static java.util.stream.Collectors.joining;

/**
 * Maven mojo to generate OpenAPI documentation document based on Swagger.
 */
@Mojo(
        name = "generate",
        defaultPhase = LifecyclePhase.PREPARE_PACKAGE,
        requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME
)
public class GenerateMojo extends AbstractMojo {

    private final Log log = getLog();

    @Parameter(defaultValue = "${project.artifactId}")
    private String title;

    @Parameter(defaultValue = "${project.version}")
    private String version;

    @Parameter
    private Set<String> actionPackages;

    @Parameter(defaultValue = "${project.build.directory}/classes/apps/${project.artifactId}/docs")
    private File outputDirectory;

    @Component
    private MavenProject project;

    @Override
    public void execute() throws MojoExecutionException {
        log.info("Generating OpenAPI specification file");
        if (actionPackages.isEmpty()) {
            log.warn("The 'actionPackages' configuration is not specified. Processing all compile and runtime classpath classes.");
        }
        ClassLoader originalClassLoader = extendClassLoaderByProjectDependencies();
        try {
            Set<Class<?>> restActionClasses = getRestActionClasses();
            log.debug("Found " + restActionClasses.size() + " actions");
            RestActionToOpenApiPathConverter converter = new RestActionToOpenApiPathConverter(log, project.getArtifactId());
            Paths paths = new Paths();
            restActionClasses.stream()
                    .map(converter::convert)
                    .forEach(path -> paths.addPathItem(path.getLeft(), path.getRight()));
            OpenAPI openAPI = new OpenAPI()
                    .info(new Info().title(title).version(version))
                    .paths(paths);
            writeOpenApiToYaml(openAPI);
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }

    private ClassLoader extendClassLoaderByProjectDependencies() throws MojoExecutionException {
        try {
            log.debug("Extending class loader by Maven project dependencies classes");
            ClassLoader currentClassLoader = Thread.currentThread().getContextClassLoader();
            Thread.currentThread().setContextClassLoader(new ExtendedClassLoader(project, currentClassLoader));
            return currentClassLoader;
        } catch (Exception e) {
            throw new MojoExecutionException("Could not extend class loader", e);
        }
    }

    private Set<Class<?>> getRestActionClasses() {
        ConfigurationBuilder config = ConfigurationBuilder.build(actionPackages)
                .setScanners(new ResourcesScanner(), new TypeAnnotationsScanner(), new SubTypesScanner());
        return new Reflections(config).getTypesAnnotatedWith(SlingAction.class);
    }

    private void writeOpenApiToYaml(OpenAPI openApi) throws MojoExecutionException {
        String outputDirectoryPath = createOutputDirectory();
        generateAndSaveApiSpecification(openApi, outputDirectoryPath);
        saveApiHtmlPage(outputDirectoryPath);
    }

    @NotNull
    private String createOutputDirectory() throws MojoExecutionException {
        String outputDirectoryPath = outputDirectory.getPath();
        try {
            Files.createDirectories(java.nio.file.Paths.get(outputDirectoryPath));
        } catch (IOException e) {
            throw new MojoExecutionException("Could not create output directory: " + outputDirectoryPath, e);
        }
        return outputDirectoryPath;
    }

    private void generateAndSaveApiSpecification(OpenAPI openApi, String outputDirectoryPath) throws MojoExecutionException {
        File file = new File(outputDirectoryPath + "/api.yaml");
        try (PrintWriter writer = new PrintWriter(file)) {
            ObjectMapper mapper = new ObjectMapper(new YAMLFactory()).enable(SerializationFeature.INDENT_OUTPUT);
            mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
            mapper.addMixIn(Schema.class, ExcludeUnnecessaryPropertiesMixin.class);
            mapper.writeValue(writer, openApi);
            log.info("OpenAPI specification saved to " + file.getAbsolutePath());
        } catch (IOException e) {
            throw new MojoExecutionException("Error while saving OpenAPI specification file", e);
        }
    }

    private void saveApiHtmlPage(String outputDirectoryPath) throws MojoExecutionException {
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(getClass().getResourceAsStream("/api.html")));
            String pageHtml = reader.lines()
                    .map(line -> line.contains("${title}") ? line.replace("${title}", title) : line)
                    .collect(joining());
            Files.copy(new ByteArrayInputStream(pageHtml.getBytes()), java.nio.file.Paths.get(outputDirectoryPath + "/api.html"),
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new MojoExecutionException("Could not save API HTML file", e);
        }
    }

}

/**
 * Needed to get rid of redundant `exampleSetFlag` values from Schema class.
 * This mixin allows to ignore it during serialization to YAML.
 */
interface ExcludeUnnecessaryPropertiesMixin {
    @JsonIgnore
    @SuppressWarnings("unused")
    boolean getExampleSetFlag();
}
