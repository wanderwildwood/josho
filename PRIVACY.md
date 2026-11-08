# Privacy

Engraver asks for nothing and sends nothing, because it has no way to send anything anywhere.

That is the whole policy. The rest of this page is the evidence for it.

## No permissions

`app/src/main/AndroidManifest.xml` declares none. There is no `android.permission.INTERNET`,
so the app cannot open a network connection even by accident: Android refuses it at the socket.
LilyPond runs inside the app, on the device, under the same rule.

The folder your scores live in is not reached through a storage permission. You pick it with a
folder chooser (Cabinet's, when Cabinet is installed, otherwise the phone's own), and the grant
that comes back covers that folder and nothing else.

## Where your scores are

In that folder, as the plain `.ly` files you can see there, with the PDF (and MIDI, if a score
asks for one) written beside each score. While compiling, the app copies the folder's LilyPond
files into its own storage so LilyPond can follow `\include`; that copy is replaced at the next
compile and goes when the app is uninstalled. Nothing is backed up to a cloud or carried over
in a phone-to-phone transfer.

## The one address in the app

The llama at the foot of the About opens `square.link/u/AGu8oT10` in whatever browser you
have. That is a hand-off: this app does not fetch the page, and it learns nothing about
whether you went there. The page is Square's, a separate company, and what it collects is
covered by Square's own privacy policy.

<!-- shared-about privacy section -->
## Who we are

Engraver is made by wander wildwood, an independent developer: one person, not a company. wander wildwood is the data controller for anything the app itself handles, and that is very little. Engraver keeps what you put in it on your phone and sends nothing to the developer, so the developer holds no data about you.

## How to contact us

Write to email@wanderthe.dev about your data, this policy or anything else. The same address is in the app, under About.

## Your rights

If you are in the EU, the EEA or the UK, the GDPR gives you the right to:

- see the personal data held about you (access)
- have it corrected (rectification) or deleted (erasure)
- limit how it is used (restriction)
- object to its use (objection)
- get a copy to take elsewhere (portability)
- withdraw your consent at any time, where something rests on it
- complain to a data protection supervisory authority, for example the one where you live

Similar laws elsewhere, such as the CCPA in California, give you the right to know, delete and correct, and to opt out of the sale or sharing of your data. Nothing from Engraver is sold or shared.

Since Engraver keeps what it stores only on your phone, you can exercise most of these rights yourself: delete a score's file in your folder, clear the app's data in Android's settings, or uninstall the app, which deletes all of what the app keeps (your scores stay in their folder). For anything you cannot do yourself, write to the address above.
<!-- /shared-about privacy section -->
