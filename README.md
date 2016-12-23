# Athento Workflow Extended for Nuxeo

- Validate or reject tasks from email notifications.

## Prerequisites

- Task REST API using TOKEN_AUTH of Nuxeo 6.0 dependency: *nuxeo-platform-login-token-6.0-ATH-1.0.jar*


# Workflow

## Tasks

### Auto-create workflow

You can start automatically a workflow setting-up your start task with the _autocreate_ flag to _true_. Also, you must add "_autoinitiate_" facet to the document type with which the workflow will start.