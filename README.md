Athento UI for Nuxeo
====================

Save ContentView selected columns by user
-----------------------------------------

- You can selected your favourite columns as user.



Preview without authentication
------------------------------

Preview is based on Restlet API with URL below:

GET /nuxeo/restAPI/athpreview/{repoId}/{docId}/{fieldPath}?subPath=(subPath)&amp;token=(token)

where:

- repoId: is the repository name.
- docId: is the document identifier.
- fieldPath: is the xpath of content to preview.
- subpath: is use to reference images into preview (with no access token control)
- token: is the basic control access token based on changedToken of dublincore:modified metadata.




Select a page in a ContentView
------------------------------

- You can introduce a page index into page-navigator to select your page in a ContentView.
