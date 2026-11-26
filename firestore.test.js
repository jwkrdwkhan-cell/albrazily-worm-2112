const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user cannot read or write any collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("conversations")
      .get()
  );
  await assertFails(
    unauthDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("knowledge_vault")
      .doc("k1")
      .set({
        userId: ALICE_UID,
        bookId: "b1",
        bookTitle: "Book",
        question: "Q",
        scenario: "S",
        expertAnswer: "A",
        sourceType: "book_training",
        createdAt: new Date(Date.now() - 1000),
      })
  );
});

test("Authenticated owner workspace create, update, and shadow-field rejection", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date(Date.now() - 2000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice",
      trainingLocked: false,
      trainingDays: 7,
      trainingUnlockAtMillis: 0,
      trainingStatus: "idle",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Valid partial update
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      trainingLocked: true,
      trainingDays: 14,
      trainingUnlockAtMillis: Date.now() + 86400000,
      trainingStatus: "locked",
      updatedAt: new Date(Date.now() - 500),
    })
  );

  // Reject immutable createdAt mutation
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      createdAt: new Date(Date.now() - 100),
      updatedAt: new Date(Date.now() - 100),
    })
  );

  // Reject ghost/shadow field
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: new Date(Date.now() - 100),
    })
  );

  // Reject cross-user access
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({
      displayName: "Hacked",
      updatedAt: new Date(Date.now() - 100),
    })
  );
});

test("Conversations, messages, training books, knowledge vault, and hunger reports parity", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const t0 = new Date(Date.now() - 5000);

  // Create conversation
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("conversations")
      .doc("conv_1")
      .set({
        userId: ALICE_UID,
        title: "Build SaaS App",
        contextSummary: "Building an Arabic e-commerce web app",
        messageCount: 0,
        createdAt: t0,
        updatedAt: t0,
      })
  );

  // Create message (must belong to existing conversation, no @DocumentId 'id')
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("conversations")
      .doc("conv_1")
      .collection("messages")
      .doc("msg_1")
      .set({
        userId: ALICE_UID,
        role: "model",
        content: "Here is the verified HTML site.",
        codeBlock: "<html><body>Hi</body></html>",
        codeLanguage: "html",
        deepSearchUsed: true,
        boosthisVerified: true,
        sources: ["https://example.com"],
        marketingPromptPending: false,
        createdAt: new Date(Date.now() - 1000),
      })
  );

  // Reject message with 'id' field
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("conversations")
      .doc("conv_1")
      .collection("messages")
      .doc("msg_2")
      .set({
        id: "msg_2",
        userId: ALICE_UID,
        role: "user",
        content: "Hello",
        deepSearchUsed: false,
        boosthisVerified: false,
        marketingPromptPending: false,
        createdAt: new Date(Date.now() - 1000),
      })
  );

  // Training book create & update
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("training_books")
      .doc("book_1")
      .set({
        userId: ALICE_UID,
        title: "Clean Architecture.pdf",
        mimeType: "application/pdf",
        totalChunks: 10,
        processedChunks: 0,
        qaCount: 0,
        trainingStatus: "queued",
        createdAt: t0,
        updatedAt: t0,
      })
  );

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("training_books")
      .doc("book_1")
      .update({
        processedChunks: 10,
        qaCount: 25,
        trainingStatus: "completed",
        summary: "Full mastery completed",
        updatedAt: new Date(Date.now() - 500),
      })
  );

  // Training book cannot be deleted (permanent vault)
  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("training_books")
      .doc("book_1")
      .delete()
  );

  // Knowledge vault create & permanent immutability
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("knowledge_vault")
      .doc("kv_1")
      .set({
        userId: ALICE_UID,
        bookId: "book_1",
        bookTitle: "Clean Architecture.pdf",
        question: "How should dependencies point?",
        scenario: "Designing a modular Android app",
        expertAnswer: "Dependencies must point inward toward domain policies.",
        keywords: ["architecture", "solid"],
        sourceType: "book_training",
        createdAt: new Date(Date.now() - 1000),
      })
  );

  await assertFails(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("knowledge_vault")
      .doc("kv_1")
      .delete()
  );

  // Hunger report create & update
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("hunger_reports")
      .doc("hr_1")
      .set({
        userId: ALICE_UID,
        topic: "WebGL Shaders",
        filename: "hunger_webgl_shaders.txt",
        reportContent: "Gap analysis and self-study curriculum...",
        learnedIntoVault: false,
        createdAt: t0,
      })
  );

  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("hunger_reports")
      .doc("hr_1")
      .update({
        learnedIntoVault: true,
      })
  );

  // Bob cannot list Alice's knowledge vault
  await assertFails(
    bobDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("knowledge_vault")
      .get()
  );

  // Alice can list her own collections cleanly
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("knowledge_vault")
      .get()
  );
  await assertSucceeds(
    aliceDb
      .collection("users")
      .doc(ALICE_UID)
      .collection("conversations")
      .get()
  );
});
