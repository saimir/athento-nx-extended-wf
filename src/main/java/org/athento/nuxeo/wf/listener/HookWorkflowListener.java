package org.athento.nuxeo.wf.listener;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.athento.nuxeo.wf.utils.Functions;
import org.athento.nuxeo.wf.utils.WorkflowUtils;
import org.nuxeo.ecm.core.api.*;
import org.nuxeo.ecm.core.api.event.DocumentEventCategories;
import org.nuxeo.ecm.core.event.Event;
import org.nuxeo.ecm.core.event.EventContext;
import org.nuxeo.ecm.core.event.EventListener;
import org.nuxeo.ecm.core.event.impl.DocumentEventContext;
import org.nuxeo.ecm.core.rest.AuditService;
import org.nuxeo.ecm.platform.audit.api.AuditLogger;
import org.nuxeo.ecm.platform.audit.api.LogEntry;
import org.nuxeo.ecm.platform.ec.notification.NotificationConstants;
import org.nuxeo.ecm.platform.ec.notification.NotificationListenerHook;
import org.nuxeo.ecm.platform.ec.notification.service.NotificationService;
import org.nuxeo.ecm.platform.ec.notification.service.NotificationServiceHelper;
import org.nuxeo.ecm.platform.notification.api.Notification;
import org.nuxeo.ecm.platform.task.Task;
import org.nuxeo.ecm.platform.usermanager.UserManager;
import org.nuxeo.ecm.tokenauth.service.TokenAuthenticationService;
import org.nuxeo.runtime.api.Framework;

import javax.print.Doc;
import java.io.Serializable;
import java.util.*;

/**
 * Hook for any workflow event.
 */
public class HookWorkflowListener implements NotificationListenerHook {

    private static final Log LOG = LogFactory
            .getLog(HookWorkflowListener.class);

    /** Notification service. */
    private NotificationService notificationService = NotificationServiceHelper.getNotificationService();

    private AuditLogger auditLogger = Framework.getService(AuditLogger.class);

    private UserManager userManager = null;

    /**
     * Handle notification.
     *
     * @param event
     * @throws Exception
     */
    @Override
    public void handleNotifications(Event event) throws ClientException {
        if (event != null && event.getContext() instanceof DocumentEventContext) {
            DocumentModel doc = ((DocumentEventContext) event.getContext()).getSourceDocument();
            Map<String, Serializable> properties = event.getContext().getProperties();
            Object recipientProperty = properties.get(NotificationConstants.RECIPIENTS_KEY);
            String[] recipients = null;
            if (recipientProperty != null) {
                if (recipientProperty instanceof String[]) {
                    recipients = (String[]) properties.get(NotificationConstants.RECIPIENTS_KEY);
                } else if (recipientProperty instanceof String ) {
                    recipients = new String[1];
                    recipients[0] = (String) recipientProperty;
                }
            }
            if (recipients == null) {
                return;
            }
            Set<String> users = new HashSet<String>();
            for (String recipient : recipients) {
                if (recipient == null) {
                    continue;
                }
                if (recipient.contains(NuxeoPrincipal.PREFIX)) {
                    users.add(recipient.replace(NuxeoPrincipal.PREFIX, ""));
                } else if (recipient.contains(NuxeoGroup.PREFIX)) {
                    List<String> groupMembers = getGroupMembers(recipient.replace(
                            NuxeoGroup.PREFIX, ""));
                    for (String member : groupMembers) {
                        users.add(member);
                    }
                } else {
                    if (NotificationServiceHelper.getUsersService().getGroup(
                            recipient) != null) {
                        users.addAll(getGroupMembers(recipient));
                    } else {
                        users.add(recipient);
                    }
                }

            }
            AuditLogger auditLogger = Framework.getService(AuditLogger.class);
            LogEntry entry = newEntry(doc,
                    event.getContext().getPrincipal().getName(), getStringFromSet(users));
            auditLogger.addLogEntries(Collections.singletonList(entry));
        }
    }

    private String getStringFromSet(Set<String> users) {
        StringBuffer userBuff = new StringBuffer();
        for (Iterator<String> it = users.iterator(); it.hasNext();) {
            userBuff.append(it.next() + (it.hasNext() ? ", " : ""));
        }
        return userBuff.toString();
    }

    protected LogEntry newEntry(DocumentModel doc, String principal, String users) {
        LogEntry entry = auditLogger.newLogEntry();
        entry.setEventId("emailSent");
        entry.setEventDate(new Date());
        entry.setCategory(DocumentEventCategories.EVENT_DOCUMENT_CATEGORY);
        entry.setDocUUID(doc.getId());
        entry.setDocPath(doc.getPathAsString());
        entry.setComment("Email sent to " + users);
        entry.setPrincipalName(principal);
        entry.setDocType(doc.getType());
        entry.setRepositoryId(doc.getRepositoryName());
        try {
            entry.setDocLifeCycle(doc.getCurrentLifeCycleState());
        } catch (Exception e) {
            // ignore error
        }
        return entry;
    }

    /**
     * Get group members.
     *
     * @param groupId
     * @return
     * @throws ClientException
     */
    protected List<String> getGroupMembers(String groupId)
            throws ClientException {
        return getUserManager().getUsersInGroupAndSubGroups(groupId);
    }

    /**
     * Get user manager.
     *
     * @return
     */
    protected UserManager getUserManager() {
        if (userManager == null) {
            try {
                userManager = Framework.getService(UserManager.class);
            } catch (Exception e) {
                throw new IllegalStateException(
                        "UserManager service not deployed.", e);
            }
        }
        return userManager;
    }
}
