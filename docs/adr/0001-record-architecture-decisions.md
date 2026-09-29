# 0001. Record architecture decisions

- Status: Accepted
- Date: 2026-09-29

## Context

Some choices in this project are not obvious from the code. Why a monolith and not
microservices? Why PostgreSQL and not a message broker? Why is a library allowed to bring
Jackson 2 when the rest of the code uses Jackson 3?

Six months from now, nobody remembers the answer. A new reader, or a reviewer, sees the result
but not the reasons, and not the options that were tried and rejected. Then one of two things
happens: the decision is followed blindly, or it is changed without knowing why it was made.

## Considered options

1. **Keep decisions in people's heads.** No cost today. The knowledge is lost as soon as
   people forget or leave.
2. **Write a long design document.** Covers everything once, but it goes out of date and
   nobody knows which parts still hold.
3. **Write one short record per decision (ADR).** Each record is small, dated, and never
   rewritten. When a decision changes, a new record replaces the old one.

## Decision

We use option 3: Architecture Decision Records in the
[MADR](https://adr.github.io/madr/) format, stored in `docs/adr/`.

Rules:

- One decision per file, numbered in the order the decisions are made: `NNNN-title.md`.
- Every record explains the context, the options we considered, why the others were
  rejected, and the consequences, including the bad ones.
- An accepted record is not edited. If a decision changes, we write a new record and set the
  old one to `Superseded by NNNN`.
- Plain language. Short sentences. If a sentence needs a second reading, it gets rewritten.

We write an ADR when a decision is hard to reverse, affects more than one module, or goes
against a rule or a common expectation.

## Consequences

- Good: the reasons behind the design are in the repository, next to the code, and are
  reviewed in pull requests like the code.
- Good: rejected options are visible, so the same discussion does not start again.
- Bad: writing a record takes time, and there is a risk of writing too many. Small, easily
  reversed choices do not get an ADR; the commit message is enough for them.
