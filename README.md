# Sudo Email Android SDK

## Overview
Your users will be able to easily create and add email addresses to their Sudos. Easily offer an email service to your users, only more secure and putting the user in control of their information.

Please see the [Sudo Platform Developer Docs](https://sudoplatform.com/docs) for an overview of Email in the Sudo Platform.

## Version Support
| Technology          | Supported version |
| ------------------- | ----------------- |
| Min. API Level      | 26                |
| Kotlin version      | 2.1+              |

## Integration Instructions
Visit the [Email section](https://sudoplatform.com/docs/email) of the Sudo Platform Developer Docs for SDK integration instructions.

## Key Management

Each email address's and email alias's messages are sealed to a key pair whose private key stays
on the device. Use `exportKeys()` and `importKeys(archiveData)` to move keys to another device
(importing replaces the keys already on it), and `reset()` to remove every key from the device
(for example on sign-out).

By default each email address and email alias is provisioned with a key pair of its own, unless
you supply one with `keyId`. A key pair generated for a provisioning is removed from the device
when the service rejects the provisioning. A failure that doesn't prove the service rejected it (a
transport failure, an internal service error) keeps the key, because the email address may exist.

By default the app is responsible for its key pairs (`setAllowCallerManagedKeys(true)`): it may
supply one with `keyId`, and deprovisioning never removes a key pair, so key pairs stay on the
device until `reset()`, including those the SDK generated. Build the client with
`setAllowCallerManagedKeys(false)` to have the SDK manage every key pair itself: provisioning with a
`keyId` then fails with `EmailAddressException.InvalidArgumentException` (or
`EmailAliasException.InvalidArgumentException`), and deprovisioning an email address removes its key
pair, unless another email address or email alias still uses it. Deprovisioning an email alias never
removes a key pair. If the service deprovisions the email address but the device can't remove its
key, deprovisioning throws `EmailAddressException.KeyPairRemovalFailedException`, which carries the
deprovisioned email address as `emailAddress` and the keys left on the device as `keyIds`. Treat the
email address as deprovisioned.

### One key for every email address (`enforceSingletonKeyPair`)

Apps that don't need email addresses kept cryptographically separate can share one key pair across
all of them. Messages are still sealed, but every email address's messages are sealed to the same
key, so all of them can be read with that one key:

```kotlin
val client = SudoEmailClient.builder()
    .setContext(context)
    .setSudoUserClient(sudoUserClient)
    .setEnforceSingletonKeyPair(true)
    .build()
```

The app creates the shared key pair explicitly once the user is signed in, and again after
importing keys, before provisioning. Provisioning never creates it, and fails with
`EmailAddressException.SingletonKeyPairNotFoundException` (or
`EmailAliasException.SingletonKeyPairNotFoundException`) without it:

```kotlin
val result = client.createSingletonKeysIfAbsent()
if (result.keyPair.created) {
    // The device's key material changed, so back the keys up again with exportKeys().
}
```

If the key had to be created, a key backup made before then doesn't contain it. If the user
already has email addresses, a new key also means this device doesn't have the one they're sealed
to (for example a second device), so import the user's key backup with `importKeys(archiveData)`
instead of provisioning. Calling `createSingletonKeysIfAbsent()` on a client without
`setEnforceSingletonKeyPair(true)` fails with
`EmailCryptographicKeysException.InvalidConfigurationException`. With
`setAllowCallerManagedKeys(true)`, a `keyId` supplied when provisioning is still used instead of the
shared key pair.

With `setEnforceSingletonKeyPair(true)`, deprovisioning never removes a key pair, even with
`setAllowCallerManagedKeys(false)`. The shared key pair is carried by `exportKeys()` /
`importKeys(archiveData)`, and removed only by `reset()`. If its private key is ever missing from
the device, `createSingletonKeysIfAbsent()` replaces it with a new shared key pair, and the SDK
logs an error and outputs it to the log driver (so an app's driver can record it as a non-fatal):
email addresses sealed to the missing key can't be read on that device until a key archive
containing it is imported.

## Questions and Support
File any issues you find on the project's GitHub repository. Be careful not to share any Personally Identifiable Information (PII) or sensitive account information (API keys, credentials, etc.) when reporting an issue.

For general inquiries related to the Sudo Platform, please contact [partners@sudoplatform.com](mailto:partners@sudoplatform.com)
