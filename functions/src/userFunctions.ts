import { onCall, HttpsError, CallableRequest } from "firebase-functions/v2/https";
import * as logger from "firebase-functions/logger";
import * as admin from "firebase-admin";

interface CreateUserData {
  email: string;
  password: string;
  role: string;           // full role name like "Finance Manager"
  roleType: string;       // short role identifier like "manager"
  department: string;     // e.g., "Finance"
  code: string;           // e.g., "FM"
  publicKey: string;
  mustChangePassword?: boolean;
}

export const createNewUser = onCall(async (request: CallableRequest<CreateUserData>) => {
  const {
    email,
    password,
    role,
    roleType,
    department,
    code,
    publicKey,
    mustChangePassword
  } = request.data;

  // 🔐 Auth check
  if (!request.auth) throw new HttpsError("unauthenticated", "You must be signed in.");
  if (request.auth.token.role !== "admin") throw new HttpsError("permission-denied", "Only admins can create users.");

  // 🛂 Validate required fields
  if (!email || !password || !role || !roleType || !department || !code || !publicKey) {
    throw new HttpsError("invalid-argument", "Missing required fields.");
  }

  try {
    // 🔐 Create Firebase Auth user
    const userRecord = await admin.auth().createUser({ email, password });

    // 🧾 Add custom claims for access control
    await admin.auth().setCustomUserClaims(userRecord.uid, { roleType });

    // 🗃 Save to Firestore
    await admin.firestore().collection("users").doc(userRecord.uid).set({
      email,
      role,
      roleType,
      department,
      code,
      publicKey,
      mustChangePassword: mustChangePassword === true,
      isActive: true,
      createdAt: admin.firestore.FieldValue.serverTimestamp()
    });

    return { success: true, uid: userRecord.uid };
  } catch (error: any) {
    logger.error("Error creating user:", error.message);
    throw new HttpsError("internal", error.message);
  }
});
