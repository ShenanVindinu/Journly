package uk.ac.wlv.journly;

public class User {

    private long id;
    private String email;
    private String passwordHash;
    private String salt;
    private long createdAt;

    public User() {
    }

    public User(String email, String passwordHash, String salt) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.salt = salt;
        this.createdAt = System.currentTimeMillis();
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getSalt() {
        return salt;
    }

    public void setSalt(String salt) {
        this.salt = salt;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

}
