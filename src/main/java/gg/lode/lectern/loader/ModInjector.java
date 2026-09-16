package gg.lode.lectern.loader;

import net.fabricmc.api.EnvType;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.LanguageAdapter;
import net.fabricmc.loader.api.metadata.ModDependency;
import org.spongepowered.asm.mixin.Mixins;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class ModInjector {
    private final Log log;

    ModInjector(Log log) {
        this.log = log;
    }

    void inject(Path jar) throws Exception {
        List<Path> nested = extractNestedJars(jar);

        for (Path inner : nested) {
            addToClassLoader(inner);
        }
        addToClassLoader(jar);

        for (Path inner : loadable(nested)) {
            register(inner);
        }
        register(jar);
    }

    private List<Path> loadable(List<Path> nested) throws Exception {
        Map<Path, Object> metadata = new LinkedHashMap<>();
        for (Path inner : nested) {
            Object parsed = parseMetadata(inner);
            if (parsed != null) metadata.put(inner, parsed);
        }

        Class<?> loaderModMetadata = implClass("metadata.LoaderModMetadata");
        Method getId = loaderModMetadata.getMethod("getId");
        Method getDependencies = loaderModMetadata.getMethod("getDependencies");

        Set<String> available = new HashSet<>(BUILT_IN);
        for (var mod : FabricLoader.getInstance().getAllMods()) {
            available.add(mod.getMetadata().getId());
        }

        List<Path> accepted = new ArrayList<>();

        boolean growing = true;
        while (growing) {
            growing = false;
            for (var entry : metadata.entrySet()) {
                if (accepted.contains(entry.getKey())) continue;
                String id = (String) getId.invoke(entry.getValue());
                if (available.contains(id)) {
                    log.info("Skipping nested " + id + ": already loaded");
                    accepted.add(entry.getKey());
                    continue;
                }
                if (satisfied(entry.getValue(), getDependencies, available)) {
                    accepted.add(entry.getKey());
                    available.add(id);
                    growing = true;
                }
            }
        }

        List<Path> out = new ArrayList<>();
        for (var entry : metadata.entrySet()) {
            String id = (String) getId.invoke(entry.getValue());
            if (accepted.contains(entry.getKey()) && !FabricLoader.getInstance().isModLoaded(id)) {
                out.add(entry.getKey());
            } else if (!accepted.contains(entry.getKey())) {
                log.info("Skipping nested " + id + ": needs " + missing(entry.getValue(), getDependencies, available));
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private boolean satisfied(Object metadata, Method getDependencies, Set<String> available) throws Exception {
        return missing(metadata, getDependencies, available).isEmpty();
    }

    @SuppressWarnings("unchecked")
    private List<String> missing(Object metadata, Method getDependencies, Set<String> available) throws Exception {
        List<String> missing = new ArrayList<>();
        for (ModDependency dependency : (Collection<ModDependency>) getDependencies.invoke(metadata)) {
            if (dependency.getKind() != ModDependency.Kind.DEPENDS) continue;
            if (!available.contains(dependency.getModId())) missing.add(dependency.getModId());
        }
        return missing;
    }

    private static final Set<String> BUILT_IN = Set.of("minecraft", "java", "fabricloader", "fabric");

    private List<Path> extractNestedJars(Path jar) throws Exception {
        Path target = jar.resolveSibling(stripExtension(jar.getFileName().toString()) + "-jars");
        Map<String, Path> found = new LinkedHashMap<>();
        extractInto(jar, target, found, 0);
        return new ArrayList<>(found.values());
    }

    private void extractInto(Path jar, Path target, Map<String, Path> found, int depth) throws Exception {
        if (depth > 8) {
            log.warn("Stopped unpacking at " + jar.getFileName() + ": nested too deep");
            return;
        }
        List<Path> justExtracted = new ArrayList<>();
        try (FileSystem fs = FileSystems.newFileSystem(jar)) {
            Path dir = fs.getPath("META-INF", "jars");
            if (!Files.isDirectory(dir)) return;
            Files.createDirectories(target);
            try (var entries = Files.walk(dir)) {
                for (Path entry : entries.toList()) {
                    String name = entry.getFileName().toString();
                    if (!name.endsWith(".jar") || found.containsKey(name)) continue;
                    Path copy = target.resolve(name);

                    Files.copy(entry, copy, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    found.put(name, copy);
                    justExtracted.add(copy);
                    log.info("Unpacked nested jar " + name);
                }
            }
        }

        for (Path extracted : justExtracted) {
            extractInto(extracted, target, found, depth + 1);
        }
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? name : name.substring(0, dot);
    }

    private void addToClassLoader(Path jar) throws Exception {
        URL url = jar.toUri().toURL();
        try {
            Class<?> launcherBase = implClass("launch.knot.KnotClassLoader");

            Class<?> fabricLauncherBase = implClass("launch.FabricLauncherBase");
            Object launcher = fabricLauncherBase.getMethod("getLauncher").invoke(null);
            Method addToClassPath = launcher.getClass().getMethod("addToClassPath", Path.class, String[].class);
            addToClassPath.setAccessible(true);
            addToClassPath.invoke(launcher, jar, new String[0]);
            log.info("Added " + jar.getFileName() + " to the class path via " + launcherBase.getSimpleName());
            return;
        } catch (Throwable viaLauncher) {
            log.warn("Could not add to the class path via FabricLauncherBase: " + viaLauncher);
        }

        ClassLoader classLoader = FabricLoader.class.getClassLoader();
        Method addUrl = null;
        for (Class<?> type = classLoader.getClass(); type != null; type = type.getSuperclass()) {
            try {
                addUrl = type.getDeclaredMethod("addURL", URL.class);
                break;
            } catch (NoSuchMethodException ignored) {
            }
        }
        if (addUrl == null) {
            throw new IllegalStateException("No way to add " + jar.getFileName() + " to " + classLoader);
        }
        addUrl.setAccessible(true);
        addUrl.invoke(classLoader, url);
        log.info("Added " + jar.getFileName() + " to the class path by reflection");
    }

    @SuppressWarnings("unchecked")
    private void register(Path jar) throws Exception {
        Object metadata = parseMetadata(jar);
        if (metadata == null) {
            log.info(jar.getFileName() + " has no fabric.mod.json; class path only");
            return;
        }

        Class<?> loaderModMetadata = implClass("metadata.LoaderModMetadata");
        String id = (String) loaderModMetadata.getMethod("getId").invoke(metadata);
        if (FabricLoader.getInstance().isModLoaded(id)) {
            throw new IllegalStateException("Mod " + id + " is already loaded; refusing to inject a second copy");
        }

        Method getMixinConfigs = loaderModMetadata.getMethod("getMixinConfigs", EnvType.class);
        for (String config : (Collection<String>) getMixinConfigs.invoke(metadata, EnvType.CLIENT)) {
            Mixins.addConfiguration(config);
            log.info("Registered mixin config " + config);
        }

        Object container = newModContainer(metadata, jar);
        Object fabricLoader = FabricLoader.getInstance();
        Class<?> loaderClass = implClass("FabricLoaderImpl");

        List<Object> mods = (List<Object>) field(loaderClass, "mods").get(fabricLoader);
        Map<String, Object> modMap = (Map<String, Object>) field(loaderClass, "modMap").get(fabricLoader);
        Object entrypointStorage = field(loaderClass, "entrypointStorage").get(fabricLoader);
        Map<String, LanguageAdapter> adapterMap =
                (Map<String, LanguageAdapter>) field(loaderClass, "adapterMap").get(fabricLoader);

        mods.add(container);
        modMap.put(id, container);

        Method add = entrypointStorage.getClass()
                .getDeclaredMethod("add", implClass("ModContainerImpl"), String.class,
                        implClass("metadata.EntrypointMetadata"), Map.class);
        add.setAccessible(true);
        Method getEntrypointKeys = loaderModMetadata.getMethod("getEntrypointKeys");
        Method getEntrypoints = loaderModMetadata.getMethod("getEntrypoints", String.class);
        for (String key : (Collection<String>) getEntrypointKeys.invoke(metadata)) {
            for (Object entry : (List<Object>) getEntrypoints.invoke(metadata, key)) {
                add.invoke(entrypointStorage, container, key, entry, adapterMap);
            }
        }

        applyClassTweaker(jar, metadata, id);

        log.info("Registered " + id + " " + loaderModMetadata.getMethod("getVersion").invoke(metadata));
    }

    private void applyClassTweaker(Path jar, Object metadata, String id) {
        try {
            Class<?> loaderModMetadata = implClass("metadata.LoaderModMetadata");
            String file = null;
            for (String getter : new String[]{"getClassTweaker", "getAccessWidener"}) {
                try {
                    file = (String) loaderModMetadata.getMethod(getter).invoke(metadata);
                    break;
                } catch (NoSuchMethodException tryTheOtherName) {
                }
            }
            if (file == null || file.isBlank()) return;

            byte[] definition;
            try (FileSystem fs = FileSystems.newFileSystem(jar)) {
                Path path = fs.getPath(file);
                if (!Files.exists(path)) {
                    log.warn(id + " names the widener " + file + ", which is not in its jar");
                    return;
                }
                definition = Files.readAllBytes(path);
            }

            Object loader = FabricLoader.getInstance();
            Object target;
            String readerClass;
            try {
                target = loader.getClass().getMethod("getClassTweaker").invoke(loader);
                readerClass = "lib.classtweaker.api.ClassTweakerReader";
            } catch (NoSuchMethodException older) {
                target = loader.getClass().getMethod("getAccessWidener").invoke(loader);
                readerClass = "lib.accesswidener.AccessWidenerReader";
            }

            Class<?> reader = implClass(readerClass);
            Class<?> visitor = readerClass.contains("classtweaker")
                    ? implClass("lib.classtweaker.api.visitor.ClassTweakerVisitor")
                    : implClass("lib.accesswidener.AccessWidenerVisitor");
            Object instance;
            try {
                instance = reader.getMethod("create", visitor).invoke(null, target);
            } catch (NoSuchMethodException notAFactory) {
                instance = reader.getConstructor(visitor).newInstance(target);
            }

            Object launcher = implClass("launch.FabricLauncherBase").getMethod("getLauncher").invoke(null);
            Object mappings = launcher.getClass().getMethod("getMappingConfiguration").invoke(launcher);
            String namespace = (String) mappings.getClass()
                    .getMethod("getRuntimeNamespace").invoke(mappings);

            reader.getMethod("read", byte[].class, String.class).invoke(instance, definition, namespace);
            log.info("Applied the access widener from " + id + " (" + namespace + ")");
        } catch (Throwable failed) {
            Throwable cause = failed instanceof java.lang.reflect.InvocationTargetException wrapped
                    && wrapped.getCause() != null ? wrapped.getCause() : failed;

            log.warn("Could not apply the access widener for " + id + ": " + cause);
        }
    }

    private Object newModContainer(Object metadata, Path jar) throws Exception {
        Class<?> containerClass = implClass("ModContainerImpl");
        Class<?> loaderModMetadata = implClass("metadata.LoaderModMetadata");
        URL url = jar.toUri().toURL();

        try {
            Constructor<?> ctor = containerClass.getConstructor(loaderModMetadata, URL.class);
            return ctor.newInstance(metadata, url);
        } catch (NoSuchMethodException older) {
            Object candidate = newModCandidate(metadata, jar);
            Constructor<?> ctor = containerClass.getDeclaredConstructor(candidate.getClass());
            ctor.setAccessible(true);
            return ctor.newInstance(candidate);
        }
    }

    private Object newModCandidate(Object metadata, Path jar) throws Exception {
        Class<?> candidateClass;
        try {
            candidateClass = implClass("discovery.ModCandidateImpl");
        } catch (ClassNotFoundException older) {
            candidateClass = implClass("discovery.ModCandidate");
        }
        Class<?> loaderModMetadata = implClass("metadata.LoaderModMetadata");
        try {
            Method createPlain = candidateClass.getDeclaredMethod(
                    "createPlain", List.class, loaderModMetadata, boolean.class, Collection.class);
            createPlain.setAccessible(true);
            return createPlain.invoke(null, List.of(jar), metadata, false, Collections.emptyList());
        } catch (NoSuchMethodException singlePath) {
            Method createPlain = candidateClass.getDeclaredMethod(
                    "createPlain", Path.class, loaderModMetadata, boolean.class, Collection.class);
            createPlain.setAccessible(true);
            return createPlain.invoke(null, jar, metadata, false, Collections.emptyList());
        }
    }

    private Object parseMetadata(Path jar) throws Exception {
        try (FileSystem fs = FileSystems.newFileSystem(jar)) {
            Path json = fs.getPath("fabric.mod.json");
            if (!Files.exists(json)) return null;
            Class<?> parser = implClass("metadata.ModMetadataParser");
            Method readMetadata = parser.getDeclaredMethod("parseMetadata",
                    java.io.InputStream.class, String.class, List.class,
                    implClass("metadata.VersionOverrides"),
                    implClass("metadata.DependencyOverrides"), boolean.class);
            readMetadata.setAccessible(true);
            try (var in = Files.newInputStream(json)) {
                return readMetadata.invoke(null, in, jar.toString(), new ArrayList<>(),
                        implClass("metadata.VersionOverrides").getConstructor().newInstance(),
                        newDependencyOverrides(), false);
            }
        }
    }

    private Object newDependencyOverrides() throws Exception {
        Class<?> type = implClass("metadata.DependencyOverrides");

        Constructor<?> ctor = type.getConstructor(Path.class);
        return ctor.newInstance(FabricLoader.getInstance().getConfigDir());
    }

    private static Field field(Class<?> owner, String name) throws NoSuchFieldException {
        Field field = owner.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static Class<?> implClass(String name) throws ClassNotFoundException {
        return Class.forName("net.fabricmc.loader.impl." + name);
    }
}
