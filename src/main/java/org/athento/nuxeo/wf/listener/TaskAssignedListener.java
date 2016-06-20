package org.athento.nuxeo.wf.listener;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventContext;
import org.nuxeo.ecm.core.event.EventListener;
import org.nuxeo.ecm.core.event.impl.DocumentEventContext;
import org.nuxeo.ecm.platform.task.Task;
import org.nuxeo.ecm.tokenauth.service.TokenAuthenticationService;
import org.nuxeo.runtime.api.Framework;

import java.io.Serializable;
import java.util.Map;

/**
 * Hook for task assigned listener.
 */
public class TaskAssignedListener implements EventListener {

    private static final Log LOG = LogFactory
			.getLog(TaskAssignedListener.class);

    /** Nuxeo URL. */
    private static final String NUXEO_URL = "nuxeo.url";

    /** Token APP. */
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
                    String taskId = getTaskIdFromDocument(event.getContext());
                    if (taskId != null) {
                        // Set task id
                        properties.put("taskId", taskId);
                        if (hasContent(document)) {
                            // Set preview url
                            properties.put("previewUrl", "/restAPI/athpreview/default/" + document.getId()
                                    + "/file:content/?token=" + generatePreviewToken(document));
                        }
                        // Set back to
                        properties.put("backUrl", "/");
                        // Add access token
                        properties.put("token",
                                generateAccessTokenAuth(event.getContext().getCoreSession()));
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
     * @param session
     * @return generated access token
     */
    private String generateAccessTokenAuth(CoreSession session) {
        TokenAuthenticationService tokenAuthService = Framework.getService(TokenAuthenticationService.class);
        return tokenAuthService.acquireToken(session.getPrincipal().getName(), APP_NAME, "default", "default", "rw");
    }

    /**
     * Get task id from source document.
     *
     * @param ctxt
     * @return
     */
    private String getTaskIdFromDocument(EventContext ctxt) {
        Task task = (Task) ctxt.getProperties().get("taskInstance");
        return task.getDocument().getId();
    }

}
