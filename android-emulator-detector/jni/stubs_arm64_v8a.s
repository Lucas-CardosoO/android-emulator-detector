.global nop_arm64_v8a_beg
.global nop_arm64_v8a_end
.global fault_arm64_v8a_ldxr_beg
.global fault_arm64_v8a_ldxr_hit
.global fault_arm64_v8a_ldxr_end

nop_arm64_v8a_beg:
	nop
nop_arm64_v8a_end:

fault_arm64_v8a_ldxr_beg:
	add x0, x0, #1
fault_arm64_v8a_ldxr_hit:
	ldxr x1, [x0]
	ret
fault_arm64_v8a_ldxr_end:
	nop
