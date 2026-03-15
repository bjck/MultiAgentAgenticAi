package com.bko.orchestration.service;

/**
 * Deprecated service formerly used for DB-backed tool policies.
 * Tool access is now configured centrally without per-role DB state.
 *
 * <p>This class is retained only as a type placeholder while the UI and
 * configuration surface are being simplified.</p>
 *
 * @deprecated Pending removal once the configuration and UI no longer rely on this type.
 */
@Deprecated
public class ToolPolicyService {

    public record ToolPolicySnapshot(java.util.List<String> availableTools,
                                     java.util.List<ToolPolicyRoleView> roles) {}

    public record ToolPolicyRoleView(String phase, String code, String displayName,
                                     java.util.List<String> tools) {}

    public record ToolPolicyRoleUpdate(String phase, String code,
                                       java.util.List<String> tools) {}
}
