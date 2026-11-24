package io.github.mihaelaaghirculesei.csms.session.infrastructure;

import io.github.mihaelaaghirculesei.csms.authorization.Authorization;
import io.github.mihaelaaghirculesei.csms.session.domain.TagStatus;
import io.github.mihaelaaghirculesei.ocpp.message.AuthorizationStatus;
import io.github.mihaelaaghirculesei.ocpp.message.IdTagInfo;

/** The idTagInfo that StartTransaction and StopTransaction answer with. */
final class IdTagInfos {

  private IdTagInfos() {}

  /**
   * Combines the status decided for the session with the expiry date and group of the tag, which
   * the charge point keeps in its cache.
   */
  static IdTagInfo of(TagStatus status, Authorization tag) {
    AuthorizationStatus answer =
        switch (status) {
          case ACCEPTED -> AuthorizationStatus.ACCEPTED;
          case BLOCKED -> AuthorizationStatus.BLOCKED;
          case EXPIRED -> AuthorizationStatus.EXPIRED;
          case INVALID -> AuthorizationStatus.INVALID;
          case CONCURRENT_TX -> AuthorizationStatus.CONCURRENT_TX;
        };
    return new IdTagInfo(answer, tag.expiryDate(), tag.parentIdTag());
  }
}
