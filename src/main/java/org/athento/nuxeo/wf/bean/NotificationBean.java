package org.athento.nuxeo.wf.bean;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.athento.nuxeo.wf.utils.WorkflowUtils;
import org.jboss.seam.annotations.In;
import org.jboss.seam.annotations.Name;
import org.jboss.seam.annotations.Observer;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventContext;
import org.nuxeo.ecm.core.event.EventProducer;
import org.nuxeo.ecm.core.event.impl.DocumentEventContext;
import org.nuxeo.ecm.platform.task.Task;
import org.nuxeo.ecm.platform.task.TaskEventNames;
import org.nuxeo.ecm.platform.ui.web.api.NavigationContext;
import org.nuxeo.runtime.api.Framework;

import java.io.Serializable;

/**
 * Created by victorsanchez on 24/11/16.
 */
@Name("extWfNotificationBean")
public class NotificationBean implements Serializable {

    private Log LOG = LogFactory.getLog(NotificationBean.class);

    @In(create = true, required = false)
    protected transient CoreSession documentManager;

    @In(create = true)
    protected transient NavigationContext navigationContext;


    @Observer(value = { TaskEventNames.WORKFLOW_ENDED,
            TaskEventNames.WORKFLOW_TASK_REJECTED,
            TaskEventNames.WORKFLOW_TASK_COMPLETED,
            TaskEventNames.WORKFLOW_ABANDONED,
            TaskEventNames.WORKFLOW_CANCELED})
    public void observerAndRaiseForEvents() {
        DocumentModel currentDoc = navigationContext.getCurrentDocument();
        // Get task document from current document
        DocumentModel taskDoc = WorkflowUtils.getTaskDocument(documentManager, currentDoc);
        EventContext eventContext = new DocumentEventContext(documentManager,
                documentManager.getPrincipal(),
                currentDoc);
        eventContext.setProperty("taskInstance",  taskDoc.getAdapter(Task.class));
        Event event = eventContext.newEvent("workflowChanged");
        Framework.getLocalService(EventProducer.class).fireEvent(event);
    }

}
