package org.athento.nuxeo.wf.listener;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.IdRef;
import org.nuxeo.ecm.core.api.model.Property;
import org.nuxeo.ecm.core.api.model.impl.ListProperty;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventContext;
import org.nuxeo.ecm.core.event.EventListener;
import org.nuxeo.ecm.core.event.impl.DocumentEventContext;
import org.nuxeo.ecm.platform.ec.notification.NotificationListenerHook;
import org.nuxeo.ecm.platform.routing.core.api.TasksInfoWrapper;
import org.nuxeo.ecm.platform.routing.core.impl.GraphNode;
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
                    String taskId = getTaskIdFromDocument(event.getContext(),
                            event.getContext().getPrincipal().getName());
                    if (taskId != null) {
                        // Set task id
                        properties.put("taskId", taskId);
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
     *
     * @param ctxt
     * @param username
     * @return
     */
    private String getTaskIdFromDocument(EventContext ctxt, String username) {
        Task task = (Task) ctxt.getProperties().get("taskInstance");
        return task.getDocument().getId();
    }

}
