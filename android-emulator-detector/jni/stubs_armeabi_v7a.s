.global nop_armeabi_v7a_beg
.global nop_armeabi_v7a_end
.global fault_armeabi_v7a_ldm_beg
.global fault_armeabi_v7a_ldm_hit
.global fault_armeabi_v7a_ldm_end

/*
ldr r2, [r0]
str r2, [r0]
ldrex r2, [r0]
strex r2, r2, [r0]
ldr r2, [r0, #0]!
ldm r0, {r1, r3}
stm r0, {r2}
ldrex  r1, [r0]
strex  r1, r2, [r0]
stmib r0, {r1} // 12 - 4byte
stmib r0, {r1, r3} //12 - 8byte
ldmda r0, {r1, r3}
ldmda r0, {r1}
stmda r0, {r1, r3}
stmda r0, {r1}//
ldmdb r0, {r1, r3}
stmdb r0, {r1, r3}
*/

nop_armeabi_v7a_beg:
	nop
nop_armeabi_v7a_end:

fault_armeabi_v7a_ldm_beg:
	add r0, #1
fault_armeabi_v7a_ldm_hit:
	ldm r0, {r2}
	bx lr
fault_armeabi_v7a_ldm_end:
	nop
