(ns proja.belt-group)

;every time a belt is added, re-calc the groups and ends
;if have current group
; get last belt from group list
; look at n/s/e/w,
; if belt is facing this or same direction as this or this is facing it
;    and belt is not in group and belt is not facing this
; then valid-belt-neighbor-count++ (for this)
;
; if don't have current group
;  then create group, add belt to group, and use it for the logic
;
; if count == 1
;  then add that belt to group
; else if count > 1
;  then search for first neighbor, clockwise, starting from 0 degrees / north
;  add it to group
;  put rest on intersection list for group
; else if count == 0
;  then if intersection count > 0 take first, add to group. remove from intersection
;  if intersection count == 0, then we're done.
;
;find ends
; run this right after finding a group
; for each belt in group
; if belt has no neighbor that is facing away, then it's an end, done
; else next belt