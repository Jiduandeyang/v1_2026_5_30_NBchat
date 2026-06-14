package com.example.chat.admin;

import com.example.chat.common.ApiResponse;
import com.example.chat.common.SessionSupport;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;

@Path("/admin")
@Produces(MediaType.APPLICATION_JSON)
public class AdminResource {
    private final AdminService adminService = new AdminService();

    @GET
    @Path("/dashboard")
    public ApiResponse<DashboardStats> dashboard(@Context HttpServletRequest request) {
        SessionSupport.requireAdmin(request);
        return ApiResponse.ok(adminService.dashboard());
    }

    @GET
    @Path("/users")
    public ApiResponse<AdminPage<AdminUserRow>> users(
            @QueryParam("q") String q,
            @QueryParam("page") @DefaultValue("1") int page,
            @QueryParam("size") @DefaultValue("20") int size,
            @Context HttpServletRequest request
    ) {
        SessionSupport.requireAdmin(request);
        return ApiResponse.ok(adminService.users(q, page, size));
    }

    @PUT
    @Path("/users/{id}/role")
    public ApiResponse<String> setRole(@PathParam("id") long id, AdminRoleRequest role, @Context HttpServletRequest request) {
        long adminId = SessionSupport.requireAdmin(request);
        adminService.setUserRole(adminId, id, role.role());
        return ApiResponse.ok("ok");
    }

    @PUT
    @Path("/users/{id}/disable")
    public ApiResponse<String> setDisabled(@PathParam("id") long id, AdminDisableRequest disabled, @Context HttpServletRequest request) {
        long adminId = SessionSupport.requireAdmin(request);
        adminService.setUserDisabled(adminId, id, disabled.disabled());
        return ApiResponse.ok("ok");
    }

    @GET
    @Path("/groups")
    public ApiResponse<AdminPage<AdminGroupRow>> groups(
            @QueryParam("page") @DefaultValue("1") int page,
            @QueryParam("size") @DefaultValue("20") int size,
            @Context HttpServletRequest request
    ) {
        SessionSupport.requireAdmin(request);
        return ApiResponse.ok(adminService.groups(page, size));
    }

    @DELETE
    @Path("/groups/{conversationId}")
    public ApiResponse<String> disbandGroup(@PathParam("conversationId") long conversationId, @Context HttpServletRequest request) {
        long adminId = SessionSupport.requireAdmin(request);
        adminService.disbandGroup(adminId, conversationId);
        return ApiResponse.ok("ok");
    }

    @GET
    @Path("/moments")
    public ApiResponse<AdminPage<AdminMomentRow>> moments(
            @QueryParam("page") @DefaultValue("1") int page,
            @QueryParam("size") @DefaultValue("20") int size,
            @Context HttpServletRequest request
    ) {
        SessionSupport.requireAdmin(request);
        return ApiResponse.ok(adminService.moments(page, size));
    }

    @DELETE
    @Path("/moments/{id}")
    public ApiResponse<String> deleteMoment(@PathParam("id") long id, @Context HttpServletRequest request) {
        long adminId = SessionSupport.requireAdmin(request);
        adminService.deleteMoment(adminId, id);
        return ApiResponse.ok("ok");
    }

    @GET
    @Path("/audit-logs")
    public ApiResponse<AdminPage<AdminAuditLogRow>> auditLogs(
            @QueryParam("page") @DefaultValue("1") int page,
            @QueryParam("size") @DefaultValue("30") int size,
            @Context HttpServletRequest request
    ) {
        SessionSupport.requireAdmin(request);
        return ApiResponse.ok(adminService.auditLogs(page, size));
    }
}
