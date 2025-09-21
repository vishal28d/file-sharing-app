import * as admin from "firebase-admin";
if (!admin.apps.length) admin.initializeApp();

// ✅ ONLY this line — remove any others!
export { createNewUser } from "./userFunctions";
