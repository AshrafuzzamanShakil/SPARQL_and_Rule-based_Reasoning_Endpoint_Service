package com.example;

import org.apache.jena.query.*;
import org.apache.jena.rdf.model.*;
import org.apache.jena.reasoner.Reasoner;
import org.apache.jena.reasoner.rulesys.GenericRuleReasoner;
import org.apache.jena.reasoner.rulesys.Rule;
import org.apache.jena.util.FileManager;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;

@WebServlet("/sparql")
public class RuleBase_Servlet extends HttpServlet {

    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        String url = request.getParameter("url");
        String rules = request.getParameter("rules");
        String sparqlQuery = request.getParameter("sparql");

        Model modelFromFile = createModelFromXHTML(url);
        if (modelFromFile == null) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Unable to load RDF model from the specified URL.");
            return;
        }

        Model finalModel = applyRules(modelFromFile, rules);

        if (finalModel != null) {
            executeSPARQLQuery(finalModel, sparqlQuery, response);
        } else {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Failed to apply reasoning rules.");
        }
    }

    private Model createModelFromXHTML(String sourceURL) {
        Model modelFromFile = null;
        try {
            Reader in = Utils.getRequest(sourceURL);
            modelFromFile = ModelFactory.createDefaultModel();
            modelFromFile.read(in, "", "RDF/XML");
        } catch (IOException e) {
            System.err.println("Failed to retrieve or parse the XHTML RDFa content: " + e.getMessage());
        }
        return modelFromFile;
    }

    private Model applyRules(Model baseModel, String rulesStr) {
        try {
            String filePath = Utils.setRule(rulesStr);
            Reasoner reasoner = new GenericRuleReasoner(Rule.rulesFromURL(filePath));
            reasoner.setDerivationLogging(true);
            InfModel infModel = ModelFactory.createInfModel(reasoner, baseModel);
            return infModel;
        } catch (IOException e) {
            System.err.println("Error applying rules: " + e.getMessage());
            return null;
        }
    }

    private void executeSPARQLQuery(Model model, String sparqlQuery, HttpServletResponse response) throws IOException {
        Query query = QueryFactory.create(sparqlQuery);
        try (QueryExecution qexec = QueryExecutionFactory.create(query, model)) {
            ResultSet results = qexec.execSelect();
            response.setContentType("application/json");
            PrintWriter out = response.getWriter();
            out.print(results);
            out.flush();
        } catch (Exception e) {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
        }
    }
}
