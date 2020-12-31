package pl.ds.websight.openapi;

import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.project.MavenProject;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Class loader extended by classes from the project that the plugin is executed for.
 */
class ExtendedClassLoader extends URLClassLoader {

    public ExtendedClassLoader(MavenProject project, ClassLoader parent) throws DependencyResolutionRequiredException {
        super(getDependenciesUrls(project), parent);
    }

    private static URL[] getDependenciesUrls(MavenProject project) throws DependencyResolutionRequiredException {
        List<String> dependencies = new ArrayList<>();
        dependencies.add(project.getBuild().getOutputDirectory());
        @SuppressWarnings("unchecked") List<String> compileClasspathElements = project.getCompileClasspathElements();
        if (compileClasspathElements != null) {
            dependencies.addAll(compileClasspathElements);
        }
        @SuppressWarnings("unchecked") List<String> runtimeClasspathElements = project.getRuntimeClasspathElements();
        if (runtimeClasspathElements != null) {
            dependencies.addAll(runtimeClasspathElements);
        }
        return dependencies.stream()
                .map(ExtendedClassLoader::pathToUrl)
                .filter(Objects::nonNull)
                .toArray(URL[]::new);
    }

    private static URL pathToUrl(String dependency) {
        try {
            return Paths.get(dependency).toUri().toURL();
        } catch (MalformedURLException e) {
            return null;
        }
    }

}
