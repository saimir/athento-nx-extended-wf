package org.athento.nuxeo.wf.listener;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.athento.nuxeo.wf.utils.Functions;
import org.athento.nuxeo.wf.utils.WorkflowUtils;

import org.nuxeo.ecm.core.api.*;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventContext;
import org.nuxeo.ecm.core.event.EventListener;
import org.nuxeo.ecm.core.event.impl.DocumentEventContext;
import org.nuxeo.ecm.platform.ec.notification.NotificationConstants;
import org.nuxeo.ecm.platform.notification.api.NotificationManager;
import org.nuxeo.ecm.platform.task.Task;
import org.nuxeo.ecm.platform.task.core.service.TaskEventNotificationHelper;
import org.nuxeo.ecm.platform.usermanager.UserManager;
import org.nuxeo.ecm.tokenauth.service.TokenAuthenticationService;
import org.nuxeo.runtime.api.Framework;

import java.io.Serializable;
import java.util.*;

/**
 * Hook for task assigned listener.
 */
public class TaskAssignedListener implements EventListener {

    private static final Log LOG = LogFactory
            .getLog(TaskAssignedListener.class);

    /**
     * Nuxeo URL.
     */
    private static final String NUXEO_URL = "nuxeo.url";

    /**
     * Token APP.
     */
    private static final String APP_NAME = "athentoWorkflowExt";

    /**
     * Handle notification.
     *
     * @param event
     * @throws Exception
     */
    @Override
    public void handleEvent(Event event) throws ClientException {
        if (event != null) {
            Map<String, Serializable> properties = event.getContext().getProperties();
            // Add host property
            properties.put("host", Framework.getProperty(NUXEO_URL));
            if (event.getContext() instanceof DocumentEventContext) {
                // Add task property from event document
                DocumentModel document = ((DocumentEventContext) event.getContext()).getSourceDocument();
                if (document != null) {
                    String taskId = WorkflowUtils.getTaskIdFromDocument(event.getContext());
                    String nodeId = getNodeIdFromDocument(event.getContext());
                    if (taskId != null) {
                        // Set task id
                        properties.put("taskId", taskId);
                        boolean autoSubscribe = WorkflowUtils.readConfigValue(event.getContext().getCoreSession(),
                                "extendedWF:autoSubscribeCreator", Boolean.class);
                        if (autoSubscribe) {
                            CoreSession session = event.getContext().getCoreSession();
                            DocumentModel taskDoc = session.getDocument(new IdRef(taskId));
                            if (taskDoc != null) {
                                String initiator = (String) taskDoc.getPropertyValue("nt:initiator");
                                Map<String, Object> params = new HashMap<>();
                                params.put("toUser", initiator);
                                params.put("subject", "[Nuxeo]Task assigned " + document.getName());
                                try {
                                    WorkflowUtils.runOperation("Athento.SendNotificationTaskAssigned", document.getId(), params, session);
                                } catch (Exception e) {
                                    LOG.error("Error sending notification to initiator", e);
                                    throw new ClientException(e);
                                }
                            }
                        }
                        String doctype = document.getType();
                        if (doctype.equals("Invoice")) {
                            // Set node Id to avoid loading unknown properties in preTask
                            properties.put("nodeId", nodeId);
                            // TODO: Right now we put into properties valid and known metatadata, but this might be
                            // improved so all properties are loaded, and then the template dedices what to use
                            properties.put("docTotalAmount", document.getPropertyValue("S_FACTURA:totalAmount"));
                            properties.put("docSubject", document.getPropertyValue("S_FACTURA:subject"));
                            if (!nodeId.equals("preTask")) {
                                // In preTask, relation to the project is still not known so these next 
                                // properties can not be loaded
                                properties.put("docProjectid", document.getPropertyValue("projectFile:projectid"));
                                CoreSession session = event.getContext().getCoreSession();
                                DocumentModel project = session.getDocument(new IdRef((String) document.getPropertyValue("projectFile:projectDocid")));
                                properties.put("projectDocid", project.getId());
                                properties.put("projectBudget", project.getPropertyValue("invoicing:budget"));
                                properties.put("projectRemainingBudget", project.getPropertyValue("invoicing:remainingBudget"));
                            }
                        }
                        if (doctype.equals("ProjectFile")) {
                            // Set node Id to avoid loading unknown properties in preTask
                            properties.put("nodeId", nodeId);
                            // TODO: Right now we put into properties valid and known metatadata, but this might be
                            // improved so all properties are loaded, and then the template dedices what to use

                            properties.put("docDocid", document.getId());

                            // Invoicing values

                            properties.put("docBalance", document.getPropertyValue("invoicing:balance"));
                            properties.put("docBonusProvider", document.getPropertyValue("invoicing:bonusProvider"));
                            properties.put("docBudget", document.getPropertyValue("invoicing:budget"));
                            properties.put("docBudgetBonus", document.getPropertyValue("invoicing:budgetBonus"));
                            properties.put("docImputation", document.getPropertyValue("invoicing:imputation"));
                            properties.put("docInvoiceComments", document.getPropertyValue("invoicing:invoiceComments"));
                            properties.put("docOperativeBudget", document.getPropertyValue("invoicing:operativeBudget"));
                            properties.put("docRegion", document.getPropertyValue("invoicing:region"));
                            properties.put("docSociety", document.getPropertyValue("invoicing:society"));

                            // Marketing values

                            properties.put("docCampaignid", document.getPropertyValue("marketing:campaignid"));
                            properties.put("docCampaignDescription", document.getPropertyValue("marketing:campaignDescription"));
                            properties.put("docCampaignName", document.getPropertyValue("marketing:campaignName"));
                            properties.put("docMaterial", document.getPropertyValue("marketing:material"));
                            properties.put("docQuantity", document.getPropertyValue("marketing:quantity"));

                            // ProjectFile values

                            properties.put("docActivity", document.getPropertyValue("projectFile:activity"));
                            properties.put("docCategory", document.getPropertyValue("projectFile:category"));
                            properties.put("docEndPlannedDate", document.getPropertyValue("projectFile:endPlannedDate"));
                            properties.put("docInitialid", document.getPropertyValue("projectFile:initialid"));
                            properties.put("docProjectid", document.getPropertyValue("projectFile:projectid"));
                            properties.put("docProjectName", document.getPropertyValue("projectFile:projectName"));
                            properties.put("docProviders", document.getPropertyValue("projectFile:providers"));
                            properties.put("docSolicitantid", document.getPropertyValue("projectFile:solicitantid"));
                            properties.put("docStartPlannedDate", document.getPropertyValue("projectFile:startPlannedDate"));
                            properties.put("docSummary", document.getPropertyValue("projectFile:summary"));

                        }

                        if (hasContent(document)) {
                            // Set preview url
                            properties.put("previewUrl", "/restAPI/athpreview/default/" + document.getId()
                                    + "/file:content/?token=" + generatePreviewToken(document));
                        }
                        // Set back to
                        properties.put("backUrl", "/");
                        // Add access token
                        String actors[] = getTaskPrincipals(event.getContext());
                        HashMap<String, String> tokens = generateAccessTokensAuth(actors);
                        if (!tokens.isEmpty()) {
                            // FIXME: Now, it is only for one user by group
                            properties.put("token",
                                    tokens.values().iterator().next());
                        }
                        properties.put("tokens", tokens);

                        // Add Fn
                        if (!properties.containsKey("Fn")) {
                            properties.put("Fn", new Functions());
                        }
                    }
                }
            }
        }
    }

    /**
     * Generate a simple preview token based on dublincore:modified metadata.
     *
     * @param doc document
     * @return token
     */
    private String generatePreviewToken(DocumentModel doc) {
        // Encoding token
        return Base64.encodeBase64String(String.format("%s#control", doc.getChangeToken()).getBytes());
    }

    /**
     * Check for document content.
     *
     * @param document
     * @return
     */
    private boolean hasContent(DocumentModel document) {
        return document.getPropertyValue("file:content") != null;
    }

    /**
     * Generate access token to document based en Nuxeo Token Auth.
     *
     * @param principals
     * @return generated access token
     */
    private HashMap<String, String> generateAccessTokensAuth(String[] principals) {
        HashMap<String, String> tokens = new HashMap<>();
        TokenAuthenticationService tokenAuthService = Framework.getService(TokenAuthenticationService.class);
        for (String principal : principals) {
            tokens.put(principal, tokenAuthService.acquireToken(principal, APP_NAME, "default", "default", "rw"));
        }
        return tokens;
    }

    /**
     * Get task principals.
     *
     * @param ctxt
     * @return
     */
    private String[] getTaskPrincipals(EventContext ctxt) {
        List<String> actorResult = new ArrayList<String>();
        Task task = (Task) ctxt.getProperties().get("taskInstance");
        UserManager userManager = Framework.getService(UserManager.class);
        List<String> actors = task.getActors();
        for (String actor : actors) {
            if (actor.startsWith("group:")) {
                NuxeoGroup group = userManager.getGroup(actor.split(":")[1]);
                for (String user : group.getMemberUsers()) {
                    actorResult.add(user);
                }
            } else if (actor.startsWith("user:")) {
                actorResult.add(actor.split(":")[1]);
            } else {
                actorResult.add(actor);
            }
        }
        return actorResult.toArray(new String[0]);
    }

    /**
     * Get from source document.
     *
     * @param ctxt
     * @return
     */
    private String getNodeIdFromDocument(EventContext ctxt) {
        String nodeId = (String) ctxt.getProperties().get("nodeId");
        return nodeId;
    }

}
