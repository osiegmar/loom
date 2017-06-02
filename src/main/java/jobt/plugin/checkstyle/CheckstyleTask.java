package jobt.plugin.checkstyle;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.stream.Collectors;

import com.puppycrawl.tools.checkstyle.Checker;
import com.puppycrawl.tools.checkstyle.ConfigurationLoader;
import com.puppycrawl.tools.checkstyle.DefaultLogger;
import com.puppycrawl.tools.checkstyle.ModuleFactory;
import com.puppycrawl.tools.checkstyle.PackageObjectFactory;
import com.puppycrawl.tools.checkstyle.PropertiesExpander;
import com.puppycrawl.tools.checkstyle.api.AuditEvent;
import com.puppycrawl.tools.checkstyle.api.AuditListener;
import com.puppycrawl.tools.checkstyle.api.CheckstyleException;
import com.puppycrawl.tools.checkstyle.api.Configuration;
import com.puppycrawl.tools.checkstyle.api.RootModule;
import com.puppycrawl.tools.checkstyle.api.SeverityLevel;
import com.puppycrawl.tools.checkstyle.api.SeverityLevelCounter;

import jobt.plugin.Task;
import jobt.plugin.TaskStatus;

@SuppressWarnings("checkstyle:classdataabstractioncoupling")
public class CheckstyleTask implements Task {

    private final List<File> classPath;

    private final Path baseDir;
    private String configLocation = "config/checkstyle/checkstyle.xml";
    private boolean omitIgnoredModules = true;

    public CheckstyleTask(final String baseDir, final List<File> classPath) {
        this.baseDir = Paths.get(baseDir);
        this.classPath = classPath;
    }

    @Override
    public TaskStatus run() throws Exception {

        final List<File> collect = Files.walk(baseDir)
            .filter(Files::isRegularFile)
            .map(Path::toFile)
            .collect(Collectors.toList());

        final RootModule checker = createRootModule();

        final AuditListener[] listeners = getListeners();
        for (final AuditListener element : listeners) {
            checker.addListener(element);
        }
        final SeverityLevelCounter warningCounter =
            new SeverityLevelCounter(SeverityLevel.WARNING);
        checker.addListener(warningCounter);


        final int errors = checker.process(collect);

        if (errors == 0) {
            return TaskStatus.OK;
        }


        return TaskStatus.FAIL;
    }

    private RootModule createRootModule() throws MalformedURLException {
        final RootModule rootModule;
        final String classpath = "";

        try {
            final Properties props = createOverridingProperties();
            final Configuration config =
                ConfigurationLoader.loadConfiguration(
                    configLocation,
                    new PropertiesExpander(props),
                    omitIgnoredModules);

            final ClassLoader moduleClassLoader =
                Checker.class.getClassLoader();

            final ModuleFactory factory = new PackageObjectFactory(
                Checker.class.getPackage().getName() + ".", moduleClassLoader);

            rootModule = (RootModule) factory.createModule(config.getName());
            rootModule.setModuleClassLoader(moduleClassLoader);

            if (rootModule instanceof Checker) {
                final ClassLoader loader = buildClassLoader();

                ((Checker) rootModule).setClassLoader(loader);
            }

            rootModule.configure(config);
        } catch (final CheckstyleException ex) {
            throw new IllegalStateException(String.format(Locale.ROOT,
                "Unable to create Root Module: "
                    + "configLocation {%s}, classpath {%s}.", configLocation, classpath), ex);
//            throw new BuildException(String.format(Locale.ROOT, "Unable to create Root Module: "
//                + "configLocation {%s}, classpath {%s}.", configLocation, classpath), ex);
        }
        return rootModule;
    }

    private Properties createOverridingProperties() {
        final Properties properties = new Properties();
        properties.setProperty("samedir", "config/checkstyle");
        properties.setProperty("project_loc", "");
        return properties;
    }

    private URLClassLoader buildClassLoader() throws MalformedURLException {
        final List<URL> urls = new ArrayList<>();
        for (final File file : classPath) {
            urls.add(file.toURI().toURL());
        }
        return new URLClassLoader(urls.toArray(new URL[]{}));
    }

    @SuppressWarnings("checkstyle:regexpmultiline")
    private AuditListener[] getListeners() {
        return new AuditListener[]{new DefaultLogger(System.out, false) {
            @Override
            public void auditStarted(final AuditEvent event) {
            }

            @Override
            public void auditFinished(final AuditEvent event) {
            }
        }
        };
    }

}
