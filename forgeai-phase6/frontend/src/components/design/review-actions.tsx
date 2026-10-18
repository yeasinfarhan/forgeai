import { Check, RotateCcw, Send, Undo2 } from "lucide-react";

import { Button } from "@/components/ui/button";
import type { ReviewStatus } from "@/lib/api";

/** The review workflow buttons for the current status: submit, request changes, approve, reopen. */
export function ReviewActions({
  status,
  disabled,
  onMove,
}: {
  status: ReviewStatus;
  disabled: boolean;
  onMove: (status: ReviewStatus, message: string) => void;
}) {
  switch (status) {
    case "DRAFT":
      return (
        <Button size="sm" disabled={disabled} onClick={() => onMove("IN_REVIEW", "Submitting for review…")}>
          <Send />
          Submit for review
        </Button>
      );
    case "IN_REVIEW":
      return (
        <>
          <Button size="sm" variant="ghost" disabled={disabled} onClick={() => onMove("DRAFT", "Returning to draft…")}>
            <Undo2 />
            Request changes
          </Button>
          <Button size="sm" disabled={disabled} onClick={() => onMove("APPROVED", "Approving…")}>
            <Check />
            Approve
          </Button>
        </>
      );
    case "APPROVED":
      return (
        <Button size="sm" variant="ghost" disabled={disabled} onClick={() => onMove("DRAFT", "Reopening…")}>
          <RotateCcw />
          Reopen
        </Button>
      );
  }
}
