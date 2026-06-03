package com.innsys;

import java.io.InputStream;

import javax.security.auth.Subject;

import com.filenet.api.collection.ContentElementList;
import com.filenet.api.constants.AutoClassify;
import com.filenet.api.constants.AutoUniqueName;
import com.filenet.api.constants.CheckinType;
import com.filenet.api.constants.ClassNames;
import com.filenet.api.constants.DefineSecurityParentage;
import com.filenet.api.constants.RefreshMode;
import com.filenet.api.core.Connection;
import com.filenet.api.core.ContentTransfer;
import com.filenet.api.core.Document;
import com.filenet.api.core.Domain;
import com.filenet.api.core.Factory;
import com.filenet.api.core.Folder;
import com.filenet.api.core.ObjectStore;
import com.filenet.api.core.ReferentialContainmentRelationship;
import com.filenet.api.property.FilterElement;
import com.filenet.api.property.PropertyFilter;
import com.filenet.api.constants.PropertyNames;
import com.filenet.api.util.Id;
import com.filenet.api.util.UserContext;

/**
 * FileNet Content Services client.
 *
 * Performance design:
 *   - The TCP connection and ObjectStore handle are created ONCE (singleton)
 *     and reused across all calls.  Previously, a new connection was opened on
 *     every request.
 *   - On the first call from each BAW thread, the cached Subject is pushed onto
 *     that thread's UserContext stack and popped in a finally block.
 *   - If a FileNet call fails (e.g. server restart), the connection is reset and
 *     rebuilt automatically on the next attempt.
 */
public class FileNetCS {

    // ── Singleton ─────────────────────────────────────────────────────────────
    private static final FileNetCS INSTANCE = new FileNetCS();

    public static FileNetCS getInstance() { return INSTANCE; }

    // ── Cached, shared state ──────────────────────────────────────────────────
    private static volatile Connection   cachedConn;
    private static volatile ObjectStore  cachedOS;
    private static volatile Subject      cachedSubject;
    private static final Object          INIT_LOCK = new Object();

    // ConfigProps read once — file is small but classpath lookup has overhead
    private static final ConfigProps props = new ConfigProps();

    private FileNetCS() {
        ensureConnected();
    }

    // ── Connection lifecycle ──────────────────────────────────────────────────

    private void ensureConnected() {
        if (cachedConn == null || cachedOS == null) {
            synchronized (INIT_LOCK) {
                if (cachedConn == null || cachedOS == null) {
                    connect();
                }
            }
        }
        // Push the cached subject onto the calling thread's UserContext.
        // This must happen on every thread (UserContext is thread-local).
        pushSubjectForCurrentThread();
    }

    private void connect() {
        System.out.println("===== FileNet: opening connection =====");
        UserContext uc = new UserContext();
        UserContext.set(uc);
        try {
            Connection conn = Factory.Connection.getConnection(props.getFnURI());
            Subject subject = uc.createSubject(conn, props.getFnUser(),
                                               props.getFnPwd(), props.getFnStanza());
            uc.pushSubject(subject);
            Domain domain = Factory.Domain.getInstance(conn, null);
            ObjectStore os = Factory.ObjectStore.fetchInstance(domain, props.getFnOS(), null);
            System.out.println("===== FileNet: connected to OS=" + os.get_Name() + " =====");

            cachedConn    = conn;
            cachedSubject = subject;
            cachedOS      = os;
        } catch (Exception ex) {
            System.out.println("===== FileNet: connection FAILED =====");
            ex.printStackTrace();
            throw new RuntimeException("Cannot connect to FileNet: " + ex.getMessage(), ex);
        }
    }

    private void pushSubjectForCurrentThread() {
        UserContext uc = UserContext.get();
        if (uc == null) {
            uc = new UserContext();
            UserContext.set(uc);
        }
        uc.pushSubject(cachedSubject);
    }

    private void popSubjectForCurrentThread() {
        UserContext uc = UserContext.get();
        if (uc != null) {
            try { uc.popSubject(); } catch (Exception ignore) {}
        }
    }

    /** Invalidates the cached connection so it is rebuilt on the next call. */
    private void resetConnection() {
        synchronized (INIT_LOCK) {
            cachedConn    = null;
            cachedOS      = null;
            cachedSubject = null;
        }
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Fetches the content stream of a FileNet document by its ID.
     * The caller is responsible for closing the returned stream.
     */
    public InputStream getDocument(String templateId) {
        ensureConnected();
        try {
            return doGetDocument(templateId);
        } catch (Exception ex) {
            System.out.println("getDocument failed, retrying after reconnect: " + ex.getMessage());
            resetConnection();
            ensureConnected();
            return doGetDocument(templateId);
        } finally {
            popSubjectForCurrentThread();
        }
    }

    private InputStream doGetDocument(String templateId) {
        PropertyFilter pf = new PropertyFilter();
        pf.addIncludeProperty(new FilterElement(null, null, null, PropertyNames.CONTENT_SIZE,     null));
        pf.addIncludeProperty(new FilterElement(null, null, null, PropertyNames.CONTENT_ELEMENTS, null));

        Document doc = Factory.Document.fetchInstance(cachedOS, templateId, pf);
        System.out.println("getDocument: size=" + doc.get_ContentSize()
                           + " elements=" + doc.get_ContentElements().size());

        ContentElementList contentList = doc.get_ContentElements();
        java.util.Iterator<?> iter = contentList.iterator();
        InputStream stream = null;
        while (iter.hasNext()) {
            ContentTransfer ct = (ContentTransfer) iter.next();
            stream = ct.accessContentStream();
        }
        return stream;
    }

    /**
     * Creates a new FileNet document with the given content and returns its ID.
     * The caller is responsible for closing {@code content} after this returns.
     */
    public String createDoc(String documentTitle, String mimeType, InputStream content) {
        ensureConnected();
        try {
            return doCreateDoc(documentTitle, mimeType, content);
        } catch (Exception ex) {
            System.out.println("createDoc failed, retrying after reconnect: " + ex.getMessage());
            resetConnection();
            ensureConnected();
            return doCreateDoc(documentTitle, mimeType, content);
        } finally {
            popSubjectForCurrentThread();
        }
    }

    private String doCreateDoc(String documentTitle, String mimeType, InputStream content) {
        Document doc = Factory.Document.createInstance(cachedOS, props.getDocClass());
        doc.getProperties().putValue("DocumentTitle", documentTitle);
        doc.set_MimeType(mimeType);
        doc.save(RefreshMode.NO_REFRESH);

        ContentTransfer ct = Factory.ContentTransfer.createInstance();
        ContentElementList contentList = Factory.ContentTransfer.createList();
        ct.setCaptureSource(content);
        ct.set_RetrievalName(documentTitle + ".pdf");
        contentList.add(ct);
        doc.set_ContentElements(contentList);
        doc.save(RefreshMode.REFRESH);

        doc.checkin(AutoClassify.DO_NOT_AUTO_CLASSIFY, CheckinType.MAJOR_VERSION);
        doc.save(RefreshMode.REFRESH);

        Folder folder = Factory.Folder.getInstance(
                cachedOS, ClassNames.FOLDER, new Id(props.getFnPrintFolderId()));
        ReferentialContainmentRelationship rcr = folder.file(
                doc,
                AutoUniqueName.AUTO_UNIQUE,
                null,
                DefineSecurityParentage.DO_NOT_DEFINE_SECURITY_PARENTAGE);
        rcr.save(RefreshMode.NO_REFRESH);

        String createdId = doc.get_Id().toString();
        System.out.println("createDoc: new docId=" + createdId);
        return createdId;
    }

    public static ConfigProps getProps() { return props; }
}
