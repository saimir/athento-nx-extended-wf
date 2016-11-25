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

import java.util.HashMap;
import java.util.Map;

/**
 * Listener to notify the workflow initiator.
 */
public class NotificationToInitiatorListener implements EventListener {

    /** Log. */
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
                boolean autoSubscribe = WorkflowUtils.readConfigValue(event.getContext().getCoreSession(),
                        "extendedWF:autoSubscribeCreator", Boolean.class);
                if (autoSubscribe) {
                    CoreSession session = event.getContext().getCoreSession();
                    DocumentModel taskDoc = session.getDocument(new IdRef(taskId));
                    if (taskDoc != null) {
                        String initiator = (String) taskDoc.getPropertyValue("nt:initiator");
                        Map<String, Object> params = new HashMap<>();
                        params.put("toUser", initiator);
                        if ("workflowChanged".equals(event.getName())) {
                            params.put("template", "template:workflowChanged");
                        }
                        params.put("subject", "[Nuxeo] " + event.getName() + " " + document.getName());
                        try {
                            WorkflowUtils.runOperation("Athento.SendNotificationTaskAssigned", document.getId(), params, session);
                        } catch (Exception e) {
                            LOG.error("Error sending notification to initiator", e);
                            throw new ClientException(e);
                        }
                    }
                }
            }
        }
    }

}
