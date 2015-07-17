package io.swagger.parser;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.models.Swagger;
import io.swagger.models.auth.AuthorizationValue;
import io.swagger.models.refs.RefConstants;
import io.swagger.models.refs.RefFormat;
import io.swagger.parser.util.DeserializationUtils;
import io.swagger.parser.util.RefUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A class that caches values that have been loaded so we don't have to repeat
 * expensive operations like:
 * 1) reading a remote URL with authorization (using RemoteURL.java)
 * 2) reading the contents of a file into memory
 * 3) extracting a sub object from a json/yaml tree
 * 4) de-serializing json strings into objects
 */
public class ResolverCache {

    private static final Pattern PARAMETER_PATTERN = Pattern.compile("^" + RefConstants.INTERNAL_PARAMETER_PREFIX + "(?<name>\\S+)");
    private static final Pattern DEFINITION_PATTERN = Pattern.compile("^" + RefConstants.INTERNAL_DEFINITION_PREFIX + "(?<name>\\S+)");

    private final Swagger swagger;
    private final List<AuthorizationValue> auths;
    private Map<String, Object> resolutionCache = new HashMap<>();
    private Map<String, String> externalFileCache = new HashMap<>();

    /*
    a map that stores original external references, and their associated renamed references
     */
    private Map<String, String> renameCache = new HashMap<>();

    public ResolverCache(Swagger swagger, List<AuthorizationValue> auths) {
        this.swagger = swagger;
        this.auths = auths;
    }

    public <T> T loadRef(String ref, RefFormat refFormat, Class<T> expectedType) {
        if (refFormat == RefFormat.INTERNAL) {
            //we don't need to go get anything for internal refs
            return expectedType.cast(loadInternalRef(ref));
        }

        final String[] refParts = ref.split("#/");

        if (refParts.length > 2) {
            throw new RuntimeException("Invalid ref format: " + ref);
        }

        final String file = refParts[0];
        final String definitionPath = refParts.length == 2 ? refParts[1] : null;

        //we might have already resolved this ref, so check the resolutionCachce
        Object previouslyResolvedEntity = resolutionCache.get(ref);

        if (previouslyResolvedEntity != null) {
            T result = expectedType.cast(previouslyResolvedEntity);
            return result;
        }

        //we have not resolved this particular ref
        //but we may have already loaded the file or url in question
        String contents = externalFileCache.get(file);

        if (contents == null) {
            contents = RefUtils.readExternalRef(file, refFormat, auths);
            externalFileCache.put(file, contents);
        }

        if (definitionPath == null) {
            T result = DeserializationUtils.deserialize(contents, file, expectedType);
            resolutionCache.put(ref, result);
            return result;
        }

        //a definition path is defined, meaning we need to "dig down" through the JSON tree and get the desired entity
        JsonNode tree = DeserializationUtils.deserializeIntoTree(contents, file);

        String[] jsonPathElements = definitionPath.split("/");
        for (String jsonPathElement : jsonPathElements) {
            tree = tree.get(jsonPathElement);
            //if at any point we do find an element we expect, print and error and return null
            if (tree == null) {
                throw new RuntimeException("Could not find " + definitionPath + " in contents of " + file);
            }
        }

        T result = DeserializationUtils.deserialize(tree, file, expectedType);
        resolutionCache.put(ref, result);

        return result;
    }

    private Object loadInternalRef(String ref) {

        Object result = checkMap(ref, swagger.getParameters(), PARAMETER_PATTERN);

        if (result == null) {
            result = checkMap(ref, swagger.getDefinitions(), DEFINITION_PATTERN);
        }

        return result;

    }

    private Object checkMap(String ref, Map map, Pattern pattern) {
        final Matcher parameterMatcher = pattern.matcher(ref);

        if (parameterMatcher.matches()) {
            final String paramName = parameterMatcher.group("name");

            if (map != null) {
                return map.get(paramName);
            }
        }
        return null;
    }

    public String getRenamedRef(String originalRef) {
        return renameCache.get(originalRef);
    }

    public void putRenamedRef(String originalRef, String newRef) {
        renameCache.put(originalRef, newRef);
    }

    public Map<String, Object> getResolutionCache() {
        return Collections.unmodifiableMap(resolutionCache);
    }

    public Map<String, String> getExternalFileCache() {
        return Collections.unmodifiableMap(externalFileCache);
    }

    public Map<String, String> getRenameCache() {
        return Collections.unmodifiableMap(renameCache);
    }
}
