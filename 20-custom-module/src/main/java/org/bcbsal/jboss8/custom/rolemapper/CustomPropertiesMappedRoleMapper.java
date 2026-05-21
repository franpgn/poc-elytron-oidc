package org.bcbsal.jboss8.custom.rolemapper;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.wildfly.security.authz.RoleMapper;
import org.wildfly.security.authz.Roles;

/**
 * Implementacao minima de RoleMapper que carrega um properties file e
 * mapeia roles de entrada (vindas do token OIDC) para roles de saida
 * usadas pela aplicacao via request.isUserInRole().
 *
 * Configurado via custom-role-mapper do Elytron:
 *   property "ROLE_PROPERTIES" -> caminho do arquivo
 *
 * Formato do arquivo (entrada=saida[,saida2]):
 *   EXTERNAL_ADMIN=admin
 *   EXTERNAL_USER=user
 */
public class CustomPropertiesMappedRoleMapper implements RoleMapper {

    private final Map<String, String[]> mappings = new HashMap<>();

    public void initialize(Map<String, String> configuration) {
        String path = configuration.get("ROLE_PROPERTIES");
        if (path == null || path.isEmpty()) {
            throw new IllegalArgumentException("ROLE_PROPERTIES configuration property is required");
        }
        path = expand(path);

        Properties props = new Properties();
        try (FileInputStream fis = new FileInputStream(path)) {
            props.load(fis);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load role mapping properties from " + path, e);
        }

        for (String name : props.stringPropertyNames()) {
            String value = props.getProperty(name, "").trim();
            if (value.isEmpty()) continue;
            mappings.put(name.trim(), value.split("\\s*,\\s*"));
        }
        System.out.println("[CustomPropertiesMappedRoleMapper] Loaded " + mappings.size()
                + " role mappings from " + path);
    }

    private String expand(String path) {
        int s;
        while ((s = path.indexOf("${")) >= 0) {
            int e = path.indexOf("}", s);
            if (e < 0) break;
            String key = path.substring(s + 2, e);
            String def = "";
            int colon = key.indexOf(':');
            if (colon >= 0) {
                def = key.substring(colon + 1);
                key = key.substring(0, colon);
            }
            String val = System.getProperty(key, System.getenv().getOrDefault(key, def));
            path = path.substring(0, s) + val + path.substring(e + 1);
        }
        return path;
    }

    @Override
    public Roles mapRoles(Roles rolesToMap) {
        Set<String> out = new LinkedHashSet<>();
        for (String role : rolesToMap) {
            String[] target = mappings.get(role);
            if (target != null) {
                for (String t : target) out.add(t);
            } else {
                out.add(role);
            }
        }
        return Roles.fromSet(out);
    }
}
