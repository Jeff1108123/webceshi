# Super Admin Delete User Design

## Context

The application already has a super-admin-only management area. Super admin access is represented by `UserRole.ADMIN` and enforced on backend endpoints with `AuthService.requireAdmin`. The existing borrow limit management page lists users, roles, organizations, current borrow counts, and per-user actions, so it is the most suitable place to add a targeted delete-user action without adding a separate user management page.

## Goals

Add a super-admin action that deletes ordinary users from the system. The action must prevent accidental or unsafe deletion: super-admin accounts cannot be deleted, and users who currently have borrowed devices cannot be deleted until their devices are force-returned first.

## Non-goals

This change does not add bulk deletion, role editing, user creation, or a separate user management section. It also does not automatically force-return devices during deletion; force return remains an explicit existing admin action.

## Recommended approach

Extend the existing borrow limit management page and backend device admin controller.

This approach keeps the feature close to the current super-admin user list, avoids duplicating table/search UI, and uses the existing authorization and response patterns. A separate user management page would be cleaner for a larger user administration module, but it is unnecessary for this single action. Putting deletion in the device borrow overview would make the feature too dependent on borrow records and less suitable for ordinary users with no active devices.

## Backend design

Add a new admin-only endpoint:

`DELETE /api/devices/users/{userId}`

The controller flow matches existing admin endpoints:

1. Read the Authorization header.
2. Resolve the current user with `AuthService.requireUser`.
3. Enforce super-admin access with `AuthService.requireAdmin`.
4. Delegate deletion to backend service logic.
5. Return a success message and the refreshed user list.

The deletion service logic must:

- Return “用户不存在” when the target user ID does not exist.
- Reject deletion of ADMIN/super-admin users.
- Reject deletion when the target user still has devices in use, with a message telling the admin to force return devices before deleting.
- Delete only ordinary users with no active borrowed devices.
- Return the same user summary list shape already used by `/api/devices/users`, so the frontend can refresh from the response directly.

Backend validation is authoritative. The frontend can improve the experience, but direct API calls still must not be able to delete protected users or users with active devices.

## Frontend design

Add an API helper in `src/api/medicalColdChain.js`:

`deleteAdminUser(userId)` calls `DELETE /devices/users/{userId}`.

Update `src/components/adminBorrowLimitPage.vue` because it already displays the user list and operation column. Add a delete action for ordinary users. The UI behavior is:

- Do not offer deletion for ADMIN/super-admin rows, or keep it disabled with a clear title.
- If a user has `currentBorrowCount > 0`, clicking delete shows a message such as “该用户仍有在用设备，请先强制归还后再删除”.
- If the user has no active devices, show a confirmation dialog before calling the delete API.
- After successful deletion, sync the returned list into the existing table state and show “用户已删除”.
- On backend rejection, display the returned error using the existing `$message.error(error.message)` pattern.

## Data flow

1. Super admin opens the borrow limit management page.
2. The page loads user rows from the existing borrow limit overview endpoint.
3. Super admin clicks delete on a user row.
4. Frontend blocks immediately if the row reports active borrowed devices.
5. Frontend asks for confirmation for a deletable ordinary user.
6. Backend repeats all safety checks and deletes the target user if valid.
7. Backend returns a refreshed user list.
8. Frontend updates the table forms and rows from the returned payload.

## Error handling

The user-facing error messages should be short and action-oriented:

- Missing target user: “用户不存在”.
- Protected admin user: “不能删除超级管理员”.
- Active borrowed devices: “该用户仍有在用设备，请先强制归还后再删除”.

The backend should use the existing `BusinessException` path so frontend error handling remains consistent with current admin actions.

## Testing

Backend tests should cover:

- Super admin can delete an ordinary user with no active borrowed devices.
- Deleting a non-existent user returns the expected business error.
- Deleting an ADMIN/super-admin user is rejected.
- Deleting an ordinary user with active borrowed devices is rejected.

Frontend testing or manual verification should cover:

- The delete action appears only for ordinary users or is disabled for admins.
- Users with active borrowed devices produce the force-return-first prompt.
- Confirming deletion for an eligible user removes that user from the table.
- Backend rejection messages are displayed through the existing message component.
