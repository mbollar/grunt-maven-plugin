/*
 * Copyright 2013 original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package pl.allegro.tdr.gruntmaven;

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Parameter;
import static org.twdata.maven.mojoexecutor.MojoExecutor.*;

/**
 * Abstract mojo which uses MojoExecutor to execute exec-maven-plugin,
 * which in turn executes system command - commandline only.
 *
 * Compatible with Windows via
 * <pre>cmd /C</pre>.
 *
 * @author Adam Dubiel
 */
public abstract class AbstractExecutableMojo extends BaseMavenGruntMojo {

    /**
     * Pattern for detecting options with whitespace characters. Anything after
     * first whitespace is ignored by exec-maven-plugin, so they need to be transformed, ex:
     * --option true == --option=true
     */
    private static final Pattern WHITESPACED_OPTION_PATTERN = Pattern.compile("^-{1,2}?[\\w-]*\\s+");

    /**
     * Windows OS name.
     */
    private static final String WINDOWS_OS_FAMILY = "Windows";

    /**
     * Maven executor plugin group id.
     */
    private static final String EXEC_MAVEN_GROUP = "org.codehaus.mojo";

    /**
     * Maven executor plugin artifact id.
     */
    private static final String EXEC_MAVEN_ARTIFACT = "exec-maven-plugin";

    /**
     * Maven executor plugin goal.
     */
    private static final String EXEC_GOAL = "exec";

    private static final String EXEC_SUCCESS_CODES_ELEMENT = "successCodes";

    private static final String EXEC_SUCCESS_CODE_ELEMENT = "successCode";

    /**
     * Inject building OS name.
     */
    @Parameter(defaultValue = "${os.name}")
    private String osName;

    /**
     * Should npm/grunt print colors in output (default to false).
     */
    @Parameter(property = "showColors", defaultValue = "false")
    protected boolean showColors;

    /**
     * Version of exec plugin to use (defaults to 1.2.1).
     */
    @Parameter(property = "execMavenPluginVersion", defaultValue = "1.2.1")
    protected String execMavenPluginVersion;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        Element[] configuration = buildConfigForOS();
        Element customSuccessCodes = overwriteSuccessCodes();
        if (customSuccessCodes != null) {
            configuration = concat(configuration, customSuccessCodes);
        }

        executeMojo(plugin(
                groupId(EXEC_MAVEN_GROUP),
                artifactId(EXEC_MAVEN_ARTIFACT),
                version(execMavenPluginVersion)),
                goal(EXEC_GOAL),
                configuration(configuration),
                executionEnvironment(mavenProject, mavenSession, pluginManager));
    }

    /**
     * Create
     * <pre>configuration</pre> element for host OS.
     *
     * @return <pre>configuration</pre> element
     */
    private Element[] buildConfigForOS() {
        Element[] configuration;

        getLog().info("OS Name: " + osName);

        if (osName.toUpperCase().contains(WINDOWS_OS_FAMILY.toUpperCase())) {
            configuration = buildConfigForWindows();
        } else {
            configuration = buildConfigForProperOS();
        }

        configuration = concat(configuration, new Element[]{element(name("workingDirectory"), gruntBuildDirectory)});
        return configuration;
    }

    /**
     * Create
     * <pre>configuration</pre> element for proper *nix OSes.
     *
     * @return configuration
     */
    private Element[] buildConfigForProperOS() {
        Element[] osConfiguration = new Element[]{
            element(name("executable"), getExecutable()),
            element(name("arguments"), getArguments())
        };

        return osConfiguration;
    }

    /**
     * Create
     * <pre>configuration</pre> element for strange Windows OS.
     *
     * @return configuration
     */
    private Element[] buildConfigForWindows() {
        Element[] arguments = new Element[]{
            element(name("argument"), "/C"),
            element(name("argument"), getExecutable())
        };
        arguments = concat(arguments, getArguments());

        Element[] osConfiguration = new Element[]{
            element(name("executable"), "cmd"),
            element(name("arguments"), arguments)
        };

        return osConfiguration;
    }

    /**
     * Don't want to use any dependencies like Apache Commons - sorry.
     *
     * @param <T>    type of array entries
     * @param array1 first array
     * @param array2 second array
     * @return first array + second array
     */
    protected <T> T[] concat(T[] array1, T... array2) {
        T[] result = Arrays.copyOf(array1, array1.length + array2.length);
        System.arraycopy(array2, 0, result, array1.length, array2.length);
        return result;
    }

    /**
     * Return executable form maven exec
     * <pre>executable element</pre>, return
     * executable name for *nix OS, not Windows!
     *
     * @return executable name
     */
    protected abstract String getExecutable();

    /**
     * Return custom executable arguments.
     *
     * @return arguments
     */
    protected abstract Element[] getArguments();

    private Element overwriteSuccessCodes() {
        String[] customCodes = customSuccessCodes();
        if (customCodes == null) {
            return null;
        }

        Element[] successCodesElements = new Element[customCodes.length];
        for (int index = 0; index < customCodes.length; ++index) {
            successCodesElements[index] = element(EXEC_SUCCESS_CODE_ELEMENT, customCodes[index]);
        }
        return element(EXEC_SUCCESS_CODES_ELEMENT, successCodesElements);
    }

    /**
     * Return custom
     * <pre>successCodes</pre> section of maven-exec-plugin.
     * Return null if default (0) is fine.
     */
    protected String[] customSuccessCodes() {
        return null;
    }

    /**
     * Normalization checks if argument contains whitespace character between
     * argument name and it's value, if so it replaces the whitespace with provided
     * replacement. Normalization is needed, because mojo-exec discards all
     * characters after first whitespace.
     */
    protected String normalizeArgument(String argument, String whitespaceReplacement) {
        Matcher matcher = WHITESPACED_OPTION_PATTERN.matcher(argument);
        if (matcher.find()) {
            return argument.replaceFirst("\\s+", "=");
        }
        return argument;
    }
}
