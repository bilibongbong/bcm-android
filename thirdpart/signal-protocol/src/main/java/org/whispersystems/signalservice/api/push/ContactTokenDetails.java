/**
 * Copyright (C) 2014-2016 Open Whisper Systems
 *
 * Licensed according to the LICENSE file in this repository.
 */

package org.whispersystems.signalservice.api.push;

/**
 * A class that represents a contact's registration state.
 */
public class ContactTokenDetails {

  private String  token;

  private String  relay;

  private String  number;

  private boolean voice;

  private boolean video;

  public ContactTokenDetails() {}

  /**
   * @return The "anonymized" token (truncated hash) that's transmitted to the server.
   */
  public String getToken() {
    return token;
  }

  /**
   * @return The federated server this contact is registered with, or null if on your server.
   */
  public String getRelay() {
    return relay;
  }

  /**
   * @return Whether this contact supports secure voice calls.
   */
  public boolean isVoice() {
    return voice;
  }

  public boolean isVideo() {
    return video;
  }

  public void setNumber(String number) {
    this.number = number;
  }

  /**
   * @return This contact's username (e164 formatted number).
   */
  public String getNumber() {
    return number;
  }

}
