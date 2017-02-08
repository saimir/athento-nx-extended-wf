package org.athento.nuxeo.wf.listener;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.athento.nuxeo.wf.utils.Functions;
import org.athento.nuxeo.wf.utils.WorkflowUtils;

import org.nuxeo.ecm.automation.OperationContext;
import org.nuxeo.ecm.automation.core.scripting.Scripting;
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
                CoreSession session = event.getContext().getCoreSession();
                // Add task property from event document
                DocumentModel document = ((DocumentEventContext) event.getContext()).getSourceDocument();
                if (document != null) {
                    LOG.info(event.getContext().getProperties());
                    WorkflowUtils.initBindings(properties, event.getContext().getCoreSession(), document);
                    String taskId = WorkflowUtils.getTaskIdFromDocument(event.getContext());
                    String nodeId = WorkflowUtils.getNodeIdFromDocument(event.getContext());
                    if (taskId != null) {
                        // Set task id
                        properties.put("taskId", taskId);
                        String doctype = document.getType();
                        if (doctype.equals("Invoice")) {
                            // Set node Id to avoid loading unknown properties in preTask
                            properties.put("nodeId", nodeId);
                            // TODO: Right now we put into properties valid and known metatadata, but this might be
                            // improved so all properties are loaded, and then the template dedices what to use
                            properties.put("docTaxableIncome", document.getPropertyValue("S_FACTURA:taxableIncome"));
                            properties.put("docSubject", document.getPropertyValue("S_FACTURA:subject"));
                            if (!nodeId.equals("preTask") & !nodeId.equals("pre-evaluation")
                                    & !nodeId.equals("selectUserTask") & !nodeId.equals("approvalTask")) {
                                // In preTask, relation to the project is still not known so these next 
                                // properties can not be loaded
                                properties.put("docProjectid", document.getPropertyValue("projectFile:projectid"));
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

                        if (WorkflowUtils.hasContent(document)) {
                            // Set preview url
                            properties.put("previewUrl", "/restAPI/athpreview/default/" + document.getId()
                                    + "/file:content/?token=" + WorkflowUtils.generatePreviewToken(document));
                        }
                        // Set back to
                        properties.put("backUrl", "/");
                        // Add access token
                        String actors[] = WorkflowUtils.getTaskPrincipals(event.getContext());
                        HashMap<String, String> tokens = WorkflowUtils.generateAccessTokensAuth(actors);
                        if (!tokens.isEmpty()) {
                            // FIXME: Now, it is only for one user by group
                            properties.put("token",
                                    tokens.values().iterator().next());
                        }
                        properties.put("tokens", tokens);
                        boolean onlyNotifyOnChanges = WorkflowUtils.readConfigValue(event.getContext().getCoreSession(),
                                "extendedWF:onlyNotifyOnChanges", Boolean.class);
                        if (!onlyNotifyOnChanges) {
                            // Send email to others
                            boolean autoSubscribe = WorkflowUtils.readConfigValue(event.getContext().getCoreSession(),
                                    "extendedWF:autoSubscribeCreator", Boolean.class);
                            if (autoSubscribe) {
                                DocumentModel taskDoc = session.getDocument(new IdRef(taskId));
                                if (taskDoc != null) {
                                    try {
                                        String processId = (String) taskDoc.getPropertyValue("nt:processId");
                                        DocumentModel processInstance = session.getDocument(new IdRef(processId));
                                        String initiator = (String) processInstance.getPropertyValue("docri:initiator");
                                        Map<String, Object> params = new HashMap<>();
                                        params.putAll(properties);
                                        params.put("taskId", taskId);
                                        params.put("toUser", initiator);
                                        params.put("template", "template:workflowTaskAssignedGeneric");
                                        params.put("subject", "[Nuxeo]Task assigned in " + document.getName());
                                        params.put("html", true);
                                        WorkflowUtils.runOperation("Athento.SendNotificationTaskAssigned", document, params, session);
                                    } catch (Exception e) {
                                        LOG.error("Error sending notification to initiator", e);
                                        throw new ClientException(e);
                                    }
                                }
                            }
                            String autoSubscribeUsers = WorkflowUtils.readConfigValue(event.getContext().getCoreSession(),
                                    "extendedWF:autoSubscribeUsers", String.class);
                            if (autoSubscribeUsers != null) {
                                DocumentModel taskDoc = session.getDocument(new IdRef(taskId));
                                if (taskDoc != null) {
                                    String[] users = autoSubscribeUsers.split(",");
                                    for (String user : users) {
                                        try {
                                            Map<String, Object> params = new HashMap<>();
                                            params.putAll(properties);
                                            params.put("taskId", taskId);
                                            params.put("toUser", user.trim());
                                            params.put("template", "template:workflowTaskAssignedGeneric");
                                            params.put("subject", "[Nuxeo]Task assigned in " + document.getName());
                                            params.put("html", true);
                                            WorkflowUtils.runOperation("Athento.SendNotificationTaskAssigned", document, params, session);
                                        } catch (Exception e) {
                                            LOG.error("Error sending notification to user " + user, e);
                                            throw new ClientException(e);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }


}
