package org.athento.nuxeo.wf.listener;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.athento.nuxeo.wf.utils.WorkflowUtils;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventListener;
import org.nuxeo.ecm.core.event.impl.DocumentEventContext;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * Listener to notify the workflow initiator.
 */
public class NotificationToInitiatorListener implements EventListener {

    /**
     * Log.
     */
    private static final Log LOG = LogFactory
            .getLog(NotificationToInitiatorListener.class);


    /**
     * Handle notification.
     *
     * @param event
     * @throws Exception
     */
    @Override
    public void handleEvent(Event event) throws ClientException {
        if (event != null) {
            if (event.getContext() instanceof DocumentEventContext) {
                DocumentModel document = ((DocumentEventContext) event.getContext()).getSourceDocument();
                String taskId = WorkflowUtils.getTaskIdFromDocument(event.getContext());
                if (taskId == null) {
                    return;
                }
                Map<String, Serializable> properties = event.getContext().getProperties();
                WorkflowUtils.initBindings(properties, event.getContext().getCoreSession(), document);
                String nodeId = WorkflowUtils.getNodeIdFromDocument(event.getContext());
                if (taskId != null) {
                    // Set task id
                    properties.put("taskId", taskId);
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
                    properties.put("docTitle", document.getTitle());
                    properties.put("tokens", tokens);

                }
                boolean autoSubscribe = WorkflowUtils.readConfigValue(event.getContext().getCoreSession(),
                        "extendedWF:autoSubscribeCreator", Boolean.class);
                if (autoSubscribe) {
                    CoreSession session = event.getContext().getCoreSession();
                    DocumentModel taskDoc = session.getDocument(new IdRef(taskId));
                    if (taskDoc != null) {
                        try {
                            String processId = (String) taskDoc.getPropertyValue("nt:processId");
                            DocumentModel processInstance = session.getDocument(new IdRef(processId));
                            String initiator = (String) processInstance.getPropertyValue("docri:initiator");
                            Map<String, Object> params = new HashMap<>();
                            params.put("toUser", initiator);
                            params.put("taskId", taskId);
                            params.putAll(properties);
                            if ("workflowChanged".equals(event.getName())) {
                                params.put("template", "template:workflowChangedGeneric");
                            } else {
                                params.put("template", "template:workflowTaskAssignedGeneric");
                            }
                            params.put("subject", "[Nuxeo] " + event.getName() + " " + document.getName());
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
                    CoreSession session = event.getContext().getCoreSession();
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
                                params.put("subject", "[Nuxeo]Task assigned " + document.getName());
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
