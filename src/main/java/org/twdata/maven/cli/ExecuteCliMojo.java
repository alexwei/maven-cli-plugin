package org.twdata.maven.cli;

import jline.ConsoleReader;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Plugin;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.PluginManager;
import org.apache.maven.project.MavenProject;
import static org.twdata.maven.mojoexecutor.MojoExecutor.*;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.*;

/**
 * Provides an interactive command line interface for Maven plugins, allowing users to execute plugins directly.
 *
 * @requiresDependencyResolution execute
 * @goal execute
 */
public class ExecuteCliMojo
    extends AbstractMojo
{
    private final Map<String,String> defaultAliases = Collections.unmodifiableMap(new HashMap<String,String>() {{
        put("compile", "org.apache.maven.plugins:maven-compiler-plugin:compile");
        put("testCompile", "org.apache.maven.plugins:maven-compiler-plugin:testCompile");
        put("jar", "org.apache.maven.plugins:maven-jar-plugin:jar");
        put("war", "org.apache.maven.plugins:maven-war-plugin:war");
        put("resources", "org.apache.maven.plugins:maven-resources-plugin:resources");
        put("install", "org.apache.maven.plugins:maven-install-plugin:install");
        put("deploy", "org.apache.maven.plugins:maven-deploy-plugin:deploy");
        put("test", "org.apache.maven.plugins:maven-surefire-plugin:test");
        put("clean", "org.apache.maven.plugins:maven-clean-plugin:clean");
    }});

    /**
     * Command aliases.  Commands should be in the form GROUP_ID:ARTIFACT_ID:GOAL
     *
     * @parameter
     */
    private Map<String,String> commands;

    /**
     * The Maven Project Object
     *
     * @parameter expression="${project}"
     * @required
     * @readonly
     */
    protected MavenProject project;

    /**
     * The Maven Session Object
     *
     * @parameter expression="${session}"
     * @required
     * @readonly
     */
    protected MavenSession session;

    /**
     * The Maven PluginManager Object
     *
     * @component
     * @required
     */
    protected PluginManager pluginManager;

    public void execute()
        throws MojoExecutionException
    {
        // build a list of command aliases
        Map<String,String> aliases = new HashMap<String,String>();
        aliases.putAll(defaultAliases);
        aliases.putAll(commands);

        getLog().info("Waiting for commands");
        try {
            ConsoleReader reader = new ConsoleReader(System.in, new OutputStreamWriter(System.out));
            String line;

            while ((line = readCommand(reader)) != null)
            {
                if ("quit".equals(line)) {
                    break;
                } else {
                    List<MojoCall> calls = new ArrayList<MojoCall>();
                    try {
                        parseCommand(line, aliases, calls);
                    } catch (IllegalArgumentException ex) {
                        getLog().error("Invalid command: "+line);
                        continue;
                    }

                    for (MojoCall call : calls) {
                        getLog().info("Executing: "+call);
                        long start = System.currentTimeMillis();
                        executeMojo(
                            plugin(
                                    groupId(call.getGroupId()),
                                    artifactId(call.getArtifactId()),
                                    version(call.getVersion(project))
                            ),
                            goal(call.getGoal()),
                            configuration(),
                            executionEnvironment(project, session, pluginManager)
                        );
                        long now = System.currentTimeMillis();
                        getLog().info("Execution time: "+(now - start)+" ms");
                    }
                }
            }
        } catch (IOException e) {
            throw new MojoExecutionException("Unable to execute cli commands", e);
        } 
    }

    /**
     * Recursively parses commands to resolve all aliases
     * @param text The text to evaluate
     * @param aliases The list of aliases available
     * @param commands The list of commands found so far
     */
    private static void parseCommand(String text, Map<String,String> aliases, List<MojoCall> commands) {
        String[] tokens = text.split(" ");
        if (tokens.length > 1) {
            for (String token : tokens) {
                parseCommand(token, aliases, commands);
            }
        } else if (aliases.containsKey(text)) {
            parseCommand(aliases.get(text), aliases, commands);
        } else {
            String[] parsed = text.split(":");
            if (parsed.length < 3) {
                throw new IllegalArgumentException("Invalid command: "+text);
            }
            commands.add(new MojoCall(parsed[0], parsed[1], parsed[2]));
        }
    }

    private String readCommand(ConsoleReader reader) throws IOException {
        System.out.println("");
        System.out.print("maven2> ");
        return reader.readLine();
    }

    private static class MojoCall {
        private final String groupId;
        private final String artifactId;
        private final String goal;

        public MojoCall(String groupId, String artifactId, String goal) {
            this.groupId = groupId;
            this.artifactId = artifactId;
            this.goal = goal;
        }


        public String getGroupId() {
            return groupId;
        }

        public String getArtifactId() {
            return artifactId;
        }

        public String getGoal() {
            return goal;
        }

        /**
         * Tries to determine what version of the plugin has been already configured for this project.  If unknown,
         * "RELEASE" is used.
         * @param project The maven project
         * @return The discovered plugin version
         */
        public String getVersion(MavenProject project) {
            String version = null;
            List<Plugin> plugins = project.getBuildPlugins();
            for (Plugin plugin : plugins) {
                if (groupId.equals(plugin.getGroupId()) && artifactId.equals(plugin.getArtifactId())) {
                    version = plugin.getVersion();
                    break;
                }
            }

            if (version == null) {
                plugins = project.getPluginManagement().getPlugins();
                for (Plugin plugin : plugins) {
                    if (groupId.equals(plugin.getGroupId()) && artifactId.equals(plugin.getArtifactId())) {
                        version = plugin.getVersion();
                        break;
                    }
                }
            }

            if (version == null) {
                version = "RELEASE";
            }
            return version;
        }

        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append(groupId).append(":").append(artifactId);
            sb.append(" [").append(goal).append("]");
            return sb.toString();
        }
    }
}
