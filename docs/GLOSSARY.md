# Glossary

The words WTM uses, and where each one is used. One word means one thing inside a bounded context
(decision 26); where the same thing has two names, the border between contexts is where one becomes the other.

| Term | Context | Meaning |
|---|---|---|
| **Template** | Curation | The record of a picture in the library, before and after it is approved: its image, name, profile, slots, status. Aggregate `MemeTemplate`. |
| **Meme** | Discovery, Feedback, the web client | A template that is approved and can be found. Readers never see drafts or retired templates. |
| **Profile** | Curation | What describes a template: meaning, usage examples, emotions, aliases, text in the image, tags. Written by the vision model or an administrator. |
| **Description** | the user interface | The reader's word for the profile. The code says profile. |
| **Slot** | Curation | A box on the picture where a caption goes. Kept only because templates still carry them (decision 19 left the question open). |
| **Draft / Approved / Retired** | Curation | The three states of a template. Retired is final. |
| **Revise / Restore profile** | Curation | Replace the profile / put the earlier one back. Neither works on a retired template. |
| **Report** | Feedback | One person's complaint about one meme. |
| **ReportCase** | Feedback | All the reports about one meme, with its second look and its resolution. Resolving closes all of them at once. |
| **Proposal** | Feedback | What the vision model suggests after looking again. |
| **Dismiss / Resolve / Reopen** | Feedback | Close the reports without changing the meme / close them after the meme was fixed or retired / open the automatically dismissed ones again. |
| **Standing** | Feedback | How much a reporter's word weighs, built from how their past reports were treated. A read model. |
| **Favorite** | Discovery | A user keeps a meme. One per user and meme. |
| **Search index entry** | Discovery | The text and embedding a meme is found by. A read model, rebuilt from state. |
| **Event** (on the boards) | analysis only | A business fact in the past tense. The code does not publish events (decision 27). |
| **Policy** (on the boards) | analysis only | "When this happens, do that." Drawn in purple. |
