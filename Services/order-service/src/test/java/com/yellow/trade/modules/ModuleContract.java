package com.yellow.trade.modules;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * A Sprint 10 module's OpenAPI file against its code: the routes it describes
 * are the routes its controllers serve, and each schema's properties are a
 * record's fields. Written before the controller, the file is what the code
 * is held to; a drift fails the module's build.
 */
public final class ModuleContract {

    private final Map<String, Object> spec;

    private ModuleContract(Map<String, Object> spec) {
        this.spec = spec;
    }

    /** A spec under Services/order-service/openapi/, or a path from the service's folder. */
    public static ModuleContract of(String path) throws IOException {
        try (Reader reader = Files.newBufferedReader(Path.of(path))) {
            return new ModuleContract(new Yaml().load(reader));
        }
    }

    @SuppressWarnings("unchecked")
    public Set<String> describedRoutes() {
        Set<String> routes = new TreeSet<>();
        ((Map<String, Map<String, Object>>) spec.get("paths")).forEach((path, operations) ->
                operations.keySet().stream()
                        .filter(verb -> Set.of("get", "post", "put", "patch", "delete").contains(verb))
                        .forEach(verb -> routes.add(verb.toUpperCase() + " " + path)));
        return routes;
    }

    public static Set<String> servedRoutes(Class<?>... controllers) {
        Set<String> routes = new TreeSet<>();
        for (Class<?> controller : controllers) {
            RequestMapping base = controller.getAnnotation(RequestMapping.class);
            String prefix = base == null ? "" : first(base.value(), base.path());
            for (Method method : controller.getDeclaredMethods()) {
                add(routes, "GET", prefix, method.getAnnotation(GetMapping.class) == null ? null
                        : first(method.getAnnotation(GetMapping.class).value(), method.getAnnotation(GetMapping.class).path()));
                add(routes, "POST", prefix, method.getAnnotation(PostMapping.class) == null ? null
                        : first(method.getAnnotation(PostMapping.class).value(), method.getAnnotation(PostMapping.class).path()));
                add(routes, "PUT", prefix, method.getAnnotation(PutMapping.class) == null ? null
                        : first(method.getAnnotation(PutMapping.class).value(), method.getAnnotation(PutMapping.class).path()));
                add(routes, "DELETE", prefix, method.getAnnotation(DeleteMapping.class) == null ? null
                        : first(method.getAnnotation(DeleteMapping.class).value(), method.getAnnotation(DeleteMapping.class).path()));
            }
        }
        return routes;
    }

    /** The property names of a schema in components.schemas. */
    @SuppressWarnings("unchecked")
    public Set<String> properties(String schema) {
        Map<String, Object> components = (Map<String, Object>) spec.get("components");
        Map<String, Object> definition = (Map<String, Object>) ((Map<String, Object>) components.get("schemas")).get(schema);
        if (definition == null) {
            throw new IllegalArgumentException("no schema " + schema);
        }
        return new TreeSet<>(((Map<String, Object>) definition.get("properties")).keySet());
    }

    /** The codes the spec's ErrorResponse lists. */
    @SuppressWarnings("unchecked")
    public Set<String> errorCodes() {
        Map<String, Object> errorCode = (Map<String, Object>)
                ((Map<String, Object>) ((Map<String, Object>) ((Map<String, Object>) spec.get("components"))
                        .get("schemas")).get("ErrorResponse")).get("properties");
        return new TreeSet<>((java.util.List<String>) ((Map<String, Object>) errorCode.get("errorCode")).get("enum"));
    }

    public static Set<String> fields(Class<? extends Record> record) {
        return Arrays.stream(record.getRecordComponents()).map(RecordComponent::getName)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private static void add(Set<String> routes, String verb, String prefix, String path) {
        if (path != null) {
            routes.add(verb + " " + prefix + path);
        }
    }

    private static String first(String[] values, String[] paths) {
        return values.length > 0 ? values[0] : paths.length > 0 ? paths[0] : "";
    }
}
