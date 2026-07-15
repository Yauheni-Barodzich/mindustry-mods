package scs;

/** One file entry in the sync manifest. */
public class ManifestEntry {
    public String id;
    public String fileName;
    public String version;
    public String sha256;
    public long size;
    public String kind; // "mod" or "map"

    public ManifestEntry() {}

    public ManifestEntry(String id, String fileName, String version, String sha256, long size, String kind) {
        this.id = id;
        this.fileName = fileName;
        this.version = version;
        this.sha256 = sha256;
        this.size = size;
        this.kind = kind;
    }
}
