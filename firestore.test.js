const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_patient_1";
const BOB_UID = "bob_doctor_1";
const CHARLIE_UID = "charlie_stranger_1";

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

function pastDate(msOffset = 2000) {
  return new Date(Date.now() - msOffset);
}

test("Unauthenticated user: denied across all collections", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    unauthDb.collection("doctors").where("listedInDirectory", "==", true).get()
  );
  await assertFails(
    unauthDb.collection("appointments").where("patientId", "==", ALICE_UID).get()
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("medicines").get()
  );
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("reports").get()
  );
});

test("UserProfile: owner read/write succeeds; cross-user PII access and ghost fields fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = pastDate(5000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set({
      userId: ALICE_UID,
      displayName: "Alice Patient",
      email: "alice@example.com",
      bloodGroup: "O+",
      allergies: "Penicillin",
      providerMode: false,
      notificationsEnabled: true,
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Partial update succeeds
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      bloodGroup: "A+",
      providerMode: true,
      updatedAt: pastDate(1000),
    })
  );

  // Ghost field rejected
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      isAdmin: true,
      updatedAt: pastDate(500),
    })
  );

  // Cross-user PII read/write rejected
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({
      displayName: "Hijacked",
      updatedAt: pastDate(500),
    })
  );

  // @DocumentId 'id' injection rejected on create
  await assertFails(
    bobDb.collection("users").doc(BOB_UID).set({
      id: BOB_UID,
      userId: BOB_UID,
      displayName: "Bob",
      email: "bob@example.com",
      providerMode: true,
      notificationsEnabled: true,
      createdAt: ts,
      updatedAt: ts,
    })
  );
});

test("Doctors directory & virtual consultation slots: owner writes, signed-in filtered reads, cross-user write denied", async () => {
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const ts = pastDate(5000);

  await assertSucceeds(
    bobDb.collection("doctors").doc("doc_bob").set({
      ownerUid: BOB_UID,
      name: "Dr. Bob Sterling",
      specialization: "Cardiology",
      qualifications: "MD, FACC",
      hospitalName: "City Heart Institute",
      clinicAddress: "420 Care Ave",
      phone: "+1-555-0142",
      experienceYears: 12,
      rating: 4.9,
      inPersonFee: 120,
      virtualFee: 85,
      telemedicineEnabled: true,
      listedInDirectory: true,
      avatarKey: "male",
      bio: "Preventive cardiologist offering video consults.",
      virtualSlots: ["09:00 AM", "10:30 AM", "04:00 PM"],
      clinicSlots: ["11:00 AM", "02:00 PM"],
      availableDays: ["Mon", "Wed", "Fri"],
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Doctor updates virtual consultation availability
  await assertSucceeds(
    bobDb.collection("doctors").doc("doc_bob").update({
      virtualSlots: ["09:00 AM", "10:30 AM", "04:00 PM", "06:30 PM"],
      virtualFee: 90,
      updatedAt: pastDate(1000),
    })
  );

  // Patient can query listed doctors
  await assertSucceeds(
    aliceDb.collection("doctors").where("listedInDirectory", "==", true).get()
  );

  // Unfiltered list query on doctors rejected
  await assertFails(aliceDb.collection("doctors").get());

  // Patient cannot modify Bob's doctor profile
  await assertFails(
    aliceDb.collection("doctors").doc("doc_bob").update({
      virtualFee: 1,
      updatedAt: pastDate(500),
    })
  );
});

test("Appointments, BookedSlots & TelemedicineRooms: end-to-end atomic booking, video room & chat isolation", async () => {
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const charlieDb = testEnv.authenticatedContext(CHARLIE_UID).firestore();
  const ts = pastDate(10000);

  // Seed Doctor Bob
  await assertSucceeds(
    bobDb.collection("doctors").doc("doc_bob").set({
      ownerUid: BOB_UID,
      name: "Dr. Bob Sterling",
      specialization: "Cardiology",
      qualifications: "MD, FACC",
      hospitalName: "City Heart Institute",
      clinicAddress: "420 Care Ave",
      phone: "+1-555-0142",
      experienceYears: 12,
      rating: 4.9,
      inPersonFee: 120,
      virtualFee: 85,
      telemedicineEnabled: true,
      listedInDirectory: true,
      avatarKey: "male",
      bio: "Cardiologist",
      virtualSlots: ["10:30 AM"],
      clinicSlots: ["02:00 PM"],
      availableDays: ["Mon", "Wed"],
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // Alice reserves slot + creates virtual appointment + creates telemedicine room atomically
  const slotKey = "2026-11-25_1030_AM";
  const apptId = "appt_virtual_1";
  const roomId = "room_virtual_1";

  const batch = aliceDb.batch();
  batch.set(
    aliceDb.collection("doctors").doc("doc_bob").collection("bookedSlots").doc(slotKey),
    {
      doctorId: "doc_bob",
      dateIso: "2026-11-25",
      timeSlot: "10:30 AM",
      consultationType: "VIRTUAL",
      bookedByUid: ALICE_UID,
      createdAt: ts,
    }
  );
  batch.set(aliceDb.collection("appointments").doc(apptId), {
    patientId: ALICE_UID,
    patientName: "Alice Patient",
    doctorId: "doc_bob",
    doctorOwnerUid: BOB_UID,
    doctorName: "Dr. Bob Sterling",
    department: "Cardiology",
    hospitalName: "City Heart Institute",
    consultationType: "VIRTUAL",
    dateIso: "2026-11-25",
    timeSlot: "10:30 AM",
    reason: "Follow-up blood pressure review",
    status: "UPCOMING",
    roomId,
    slotKey,
    createdAt: ts,
    updatedAt: ts,
  });
  batch.set(aliceDb.collection("telemedicineRooms").doc(roomId), {
    appointmentId: apptId,
    patientId: ALICE_UID,
    doctorOwnerUid: BOB_UID,
    participantUids: [ALICE_UID, BOB_UID],
    doctorName: "Dr. Bob Sterling",
    patientName: "Alice Patient",
    status: "WAITING",
    patientMicOn: true,
    patientCamOn: true,
    doctorMicOn: true,
    doctorCamOn: true,
    durationSeconds: 0,
    createdAt: ts,
    updatedAt: ts,
  });
  await assertSucceeds(batch.commit());

  // Patient and Doctor can query their respective appointments; Stranger cannot
  await assertSucceeds(
    aliceDb.collection("appointments").where("patientId", "==", ALICE_UID).get()
  );
  await assertSucceeds(
    bobDb.collection("appointments").where("doctorOwnerUid", "==", BOB_UID).get()
  );
  await assertFails(aliceDb.collection("appointments").get());
  await assertFails(charlieDb.collection("appointments").doc(apptId).get());

  // Doctor joins video room and sends in-call chat message; Stranger denied
  await assertSucceeds(
    bobDb.collection("telemedicineRooms").doc(roomId).update({
      status: "IN_CALL",
      doctorCamOn: true,
      updatedAt: pastDate(2000),
    })
  );
  await assertSucceeds(
    bobDb.collection("telemedicineRooms").doc(roomId).collection("messages").doc("msg_1").set({
      roomId,
      participantUids: [ALICE_UID, BOB_UID],
      senderUid: BOB_UID,
      senderName: "Dr. Bob Sterling",
      senderRole: "DOCTOR",
      text: "Hello Alice, I can see and hear you clearly.",
      createdAt: pastDate(1500),
    })
  );
  await assertSucceeds(
    aliceDb
      .collection("telemedicineRooms")
      .doc(roomId)
      .collection("messages")
      .where("participantUids", "array-contains", ALICE_UID)
      .get()
  );
  await assertFails(
    charlieDb.collection("telemedicineRooms").doc(roomId).get()
  );
  await assertFails(
    charlieDb.collection("telemedicineRooms").doc(roomId).collection("messages").doc("msg_1").get()
  );

  // End call (terminal state lock prevents subsequent updates)
  await assertSucceeds(
    aliceDb.collection("telemedicineRooms").doc(roomId).update({
      status: "ENDED",
      durationSeconds: 320,
      summaryNotes: "Continue current dosage and recheck in 2 weeks.",
      updatedAt: pastDate(500),
    })
  );
  await assertFails(
    aliceDb.collection("telemedicineRooms").doc(roomId).update({
      status: "IN_CALL",
      updatedAt: pastDate(100),
    })
  );
});

test("Medicines, DoseLogs, and MedicalReports: strict user-scoped subcollection isolation", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  const ts = pastDate(5000);

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("medicines").doc("med_1").set({
      userId: ALICE_UID,
      name: "Metformin",
      dosage: "500 mg",
      instructions: "After breakfast",
      scheduleTimes: ["08:00 AM", "08:00 PM"],
      remindersEnabled: true,
      active: true,
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("doseLogs").doc("log_1").set({
      userId: ALICE_UID,
      medicineId: "med_1",
      medicineName: "Metformin",
      dosage: "500 mg",
      dateIso: "2026-11-24",
      scheduledTime: "08:00 AM",
      status: "TAKEN",
      createdAt: ts,
      updatedAt: ts,
    })
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("reports").doc("rep_1").set({
      userId: ALICE_UID,
      title: "Lipid Panel",
      category: "Lab Results",
      reportDate: "2026-11-20",
      fileName: "lipid_panel.pdf",
      mimeType: "application/pdf",
      fileSizeBytes: 245760,
      localRelativePath: "reports/alice_patient_1/lipid_panel.pdf",
      notes: "Fasting morning sample",
      createdAt: ts,
      updatedAt: ts,
    })
  );

  // List queries succeed for owner
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("medicines").get());
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("doseLogs").get());
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).collection("reports").get());

  // Cross-user access denied
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("medicines").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("doseLogs").get());
  await assertFails(bobDb.collection("users").doc(ALICE_UID).collection("reports").get());
});
