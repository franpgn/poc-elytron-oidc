package com.example.secured;

import java.io.IOException;
import java.io.PrintWriter;
import java.security.Principal;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@WebServlet("/secured")
public class SecuredServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        resp.setContentType("text/html;charset=UTF-8");
        Principal p = req.getUserPrincipal();
        try (PrintWriter out = resp.getWriter()) {
            out.println("<html><body>");
            out.println("<h1>POC Elytron OIDC + custom role mapper</h1>");
            out.println("<p>Principal: " + (p == null ? "anonymous" : p.getName()) + "</p>");
            out.println("<h3>request.isUserInRole(...)</h3><ul>");
            for (String role : new String[] { "admin", "user", "EXTERNAL_ADMIN", "EXTERNAL_USER" }) {
                out.println("<li>" + role + " = " + req.isUserInRole(role) + "</li>");
            }
            out.println("</ul>");
            out.println("<p><a href=\"logout\">logout</a></p>");
            out.println("</body></html>");
        }
    }
}
