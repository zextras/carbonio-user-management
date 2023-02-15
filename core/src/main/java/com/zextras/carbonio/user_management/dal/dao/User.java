package com.zextras.carbonio.user_management.dal.dao;

import java.time.OffsetDateTime;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;

@Entity
@Table(name = "USER")
public class User {

  @Id
  @Column(name = "ID", length = 36, nullable = false)
  private String         id;
  @Column(name = "PICTURE_UPDATED_AT")
  @Temporal(TemporalType.TIMESTAMP)
  private OffsetDateTime pictureUpdatedAt;

  @Column(name = "STATUS_MESSAGE", length = 256, nullable = false)
  private String statusMessage = "";

  public static User create() {
    return new User();
  }

  public String getId() {
    return id;
  }

  public User id(String id) {
    this.id = id;
    return this;
  }

  public void setId(String id) {
    this.id = id;
  }

  public OffsetDateTime getPictureUpdatedAt() {
    return pictureUpdatedAt;
  }

  public void setPictureUpdatedAt(OffsetDateTime pictureUpdatedAt) {
    this.pictureUpdatedAt = pictureUpdatedAt;
  }

  public String getStatusMessage() {
    return statusMessage;
  }

  public void setStatusMessage(String statusMessage) {
    this.statusMessage = statusMessage;
  }
}
